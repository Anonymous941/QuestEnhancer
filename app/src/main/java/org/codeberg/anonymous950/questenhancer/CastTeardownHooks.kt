package org.codeberg.anonymous950.questenhancer

import android.os.Looper
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import org.json.JSONObject
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Cast stop/start handshake fixes (com.oculus.vrcast + com.oculus.horizon).
 *
 * Found in the decompiled vrcast (VrCastService v10) and Horizon:
 *
 * 1. Stop never tells the TV. GoogleCastConnectionHelper.disconnect() -> GoogleCastMWSBridge.close()
 *    only sends CastV2 connection CLOSEs and drops the TCP socket. It never sends the receiver
 *    STOP {sessionId}, and the sessionId from LAUNCH is thrown away
 *    (ReceiverPlatformSession.lambda$launch$0). The TV keeps the mirroring receiver app
 *    (DC4AF9FD) running with nobody attached: the "fake cast".
 *    Fix: remember the sessionId at LAUNCH, send STOP on every close and wait for the TV's reply.
 *
 * 2. Start ignores a leftover cast. connect() never checks mGoogleCastMWSBridge. If the last cast
 *    was not cleaned up, the new bridge LAUNCHes on the TV, then fails to bind localhost:8890
 *    (still held), and leaves another orphaned receiver app on the TV.
 *    Fix: tear down a leftover bridge first; before LAUNCH, ask the TV for its status and STOP
 *    any instance of OUR receiver app (only DC4AF9FD, never another sender's app).
 *
 * 3. A failure handler fires 500 ms late (AnonymousClass2.onFailure -> postDelayed disconnect)
 *    and disconnects whatever cast is current, which can be the NEXT cast.
 *    Fix: skip it unless the failed bridge is still the current one.
 *
 * 4. Horizon only forwards stop while its device state is CASTING
 *    (VrCastManager.stopCast/stopCastWithError gate on VrCastDeviceUtils.isCasting), so a cast
 *    stuck in CONNECTING is never stopped in vrcast.
 *    Fix: inside those two stop paths, let any known device through.
 *
 * On by default; su -c setprop debug.qe.cast_teardown 0 turns all of it off (read per call).
 */
object CastTeardownHooks {
    private const val TAG = "QuestEnhancer/CastTeardown"
    private const val PKG = "com.oculus.vrcast.googlecast"
    private const val HELPER = "$PKG.GoogleCastConnectionHelper"
    private const val BRIDGE = "$PKG.GoogleCastMWSBridge"
    private const val PLATFORM = "$PKG.net.ReceiverPlatformSession"
    private const val NATIVE_RECEIVER_ID = "DC4AF9FD"
    private const val SESSION_FIELD = "qeReceiverSessionId"
    private const val REPLY_TIMEOUT_MS = 2000L

    // LAUNCH's reply is parsed on the device's event thread; the bridge constructor that
    // waited for it runs right after on the vrcast handler thread. One cast at a time.
    @Volatile private var lastLaunchedSessionId: String? = null

    private fun enabled(): Boolean = try {
        val sp = Class.forName("android.os.SystemProperties")
        (sp.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, "debug.qe.cast_teardown", 1) as Int) != 0
    } catch (t: Throwable) {
        true
    }

    private fun log(msg: String) = XposedBridge.log("$TAG: $msg")

    // ---------------------------------------------------------------- vrcast

    fun installVrCast(cl: ClassLoader) {
        val platform = XposedHelpers.findClass(PLATFORM, cl)
        val bridge = XposedHelpers.findClass(BRIDGE, cl)
        val helper = XposedHelpers.findClass(HELPER, cl)

        // 1a. Remember the receiver sessionId from LAUNCH's reply.
        val launchParse = XposedBridge.hookAllMethods(platform, "lambda\$launch\$0",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val app = param.result ?: return
                    val sid = XposedHelpers.getObjectField(app, "sessionId") as? String
                    lastLaunchedSessionId = sid
                    log("receiver app ${param.args[0]} launched, session $sid")
                }
            })

        // 1b. Attach it to the bridge that was just built from that LAUNCH.
        val ctor = XposedBridge.hookAllConstructors(bridge, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                val sid = lastLaunchedSessionId ?: return
                lastLaunchedSessionId = null
                XposedHelpers.setAdditionalInstanceField(param.thisObject, SESSION_FIELD, sid)
            }
        })

        // 1c. Before the bridge closes its channels, tell the TV to STOP our receiver app.
        val close = XposedBridge.hookAllMethods(bridge, "close", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val b = param.thisObject
                if (XposedHelpers.getBooleanField(b, "mClosed")) return
                val sid = XposedHelpers.getAdditionalInstanceField(b, SESSION_FIELD) as? String
                if (sid == null) {
                    log("close: no receiver session id known, TV not told to stop")
                    return
                }
                val ps = XposedHelpers.getObjectField(b, "mPlatformSession")
                val device = XposedHelpers.getObjectField(b, "mDevice")
                stopReceiverApp(ps, device, sid, "stop")
            }
        })

        // 2a. A leftover bridge at start: tear it down properly first.
        val connect = XposedBridge.hookAllMethods(helper, "connect", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val h = param.thisObject
                val old = XposedHelpers.getObjectField(h, "mGoogleCastMWSBridge") ?: return
                log("start: previous cast was never torn down, stopping it first")
                try {
                    XposedHelpers.callMethod(h, "disconnect")
                } catch (t: Throwable) {
                    // disconnect() needs an active device; without one, close the bridge directly.
                    log("start: disconnect() failed ($t), closing the old bridge directly")
                    try { XposedHelpers.callMethod(old, "close") } catch (t2: Throwable) { log("start: close failed: $t2") }
                    XposedHelpers.setObjectField(h, "mGoogleCastMWSBridge", null)
                }
            }
        })

        // 2b. Before LAUNCH: if the TV still runs our receiver app (orphaned), STOP it so the
        //     new cast gets a fresh receiver instead of a half-dead one.
        val launch = XposedBridge.hookAllMethods(platform, "launch", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!enabled()) return
                val appId = param.args[0] as? String ?: return
                if (appId != NATIVE_RECEIVER_ID) return
                val ps = param.thisObject
                val device = XposedHelpers.getObjectField(ps, "mDevice")
                if (onEventThread(device)) return   // would deadlock waiting for the reply
                try {
                    val fut = XposedHelpers.callMethod(ps, "getStatus") as CompletableFuture<*>
                    val status = fut.get(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS) as JSONObject
                    val apps = status.optJSONObject("status")?.optJSONArray("applications")
                    if (apps == null || apps.length() == 0) {
                        log("start: TV is idle")
                        return
                    }
                    for (i in 0 until apps.length()) {
                        val a = apps.getJSONObject(i)
                        val id = a.optString("appId")
                        if (id == NATIVE_RECEIVER_ID) {
                            log("start: TV still runs our receiver (session ${a.optString("sessionId")}), stopping it")
                            stopReceiverApp(ps, device, a.optString("sessionId"), "start")
                        } else {
                            log("start: TV runs app $id (${a.optString("displayName")}), left alone")
                        }
                    }
                } catch (t: Throwable) {
                    log("start: GET_STATUS failed ($t), launching anyway")
                }
            }
        })

        // 3. Late failure handler: only act on the bridge that failed if it is still current.
        val onFailure = XposedHelpers.findClassIfExists("$HELPER\$2", cl)?.let { anon ->
            XposedBridge.hookAllMethods(anon, "lambda\$onFailure\$0\$GoogleCastConnectionHelper\$2",
                object : XC_MethodHook() {
                    override fun beforeHookedMethod(param: MethodHookParam) {
                        if (!enabled()) return
                        val h = XposedHelpers.getObjectField(param.thisObject, "this\$0")
                        val current = XposedHelpers.getObjectField(h, "mGoogleCastMWSBridge")
                        if (current !== param.thisObject) {
                            log("late failure for an old cast ignored (${param.args[0]})")
                            param.result = null
                        }
                    }
                })
        }

        log("vrcast hooks: launchParse=${launchParse.size} ctor=${ctor.size} close=${close.size} " +
            "connect=${connect.size} launch=${launch.size} onFailure=${onFailure?.size ?: 0}")
    }

    /** Send receiver STOP for [sid]; wait for the TV's reply unless on the reply thread. */
    private fun stopReceiverApp(ps: Any, device: Any, sid: String, why: String) {
        try {
            if (XposedHelpers.callMethod(ps, "isClosed") as Boolean) {
                log("$why: platform session already closed, cannot send STOP")
                return
            }
            val req = JSONObject().put("type", "STOP").put("sessionId", sid)
            val fut = XposedHelpers.callMethod(ps, "sendRequest", req) as CompletableFuture<*>
            if (onEventThread(device)) {
                log("$why: STOP sent for session $sid (reply not awaited on event thread)")
                return
            }
            val reply = fut.get(REPLY_TIMEOUT_MS, TimeUnit.MILLISECONDS) as JSONObject
            val apps = reply.optJSONObject("status")?.optJSONArray("applications")
            log("$why: STOP session $sid -> ${reply.optString("type")}, apps now ${apps?.length() ?: 0}")
        } catch (t: Throwable) {
            log("$why: STOP session $sid failed: $t")
        }
    }

    /** True on the CastV2Device thread that delivers replies (blocking there would deadlock). */
    private fun onEventThread(device: Any): Boolean = try {
        val handler = XposedHelpers.getObjectField(device, "mEventHandler") as android.os.Handler
        handler.looper == Looper.myLooper()
    } catch (t: Throwable) {
        false
    }

    // ---------------------------------------------------------------- Horizon

    private val inStopPath = ThreadLocal<Boolean>()

    fun installHorizon(cl: ClassLoader) {
        val mgr = "com.oculus.horizon.service_media.vrcast.VrCastManager"
        var n = 0
        // VrCastManager$3 = stopCast's continuation, $4 = stopCastWithError's.
        for (inner in listOf("$mgr\$3", "$mgr\$4")) {
            val c = XposedHelpers.findClassIfExists(inner, cl) ?: continue
            n += XposedBridge.hookAllMethods(c, "then", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) { inStopPath.set(true) }
                override fun afterHookedMethod(param: MethodHookParam) { inStopPath.set(false) }
            }).size
        }
        n += XposedBridge.hookAllMethods(XposedHelpers.findClass(mgr, cl), "access\$200",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (inStopPath.get() != true || !enabled()) return
                    if (param.result == false && param.args[1] != null) {
                        val state = XposedHelpers.getObjectField(param.args[1], "state")
                        log("stop forwarded to vrcast although Horizon state is $state")
                        param.result = true
                    }
                }
            }).size
        log("Horizon stop-gate hooks: $n")
    }
}
