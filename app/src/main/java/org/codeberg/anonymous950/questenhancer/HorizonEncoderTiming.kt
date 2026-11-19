package org.codeberg.anonymous950.questenhancer

import android.os.SystemClock
import android.util.Log
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Times Horizon's cast video encoder (org.webrtc.MediaCodecVideoEncoder), changing
 * nothing. WebRTC hands the encoder one frame at a time and waits for it, and frames
 * captured meanwhile are lost, so this measures how long each step holds that thread.
 *
 * Every 3 seconds of a cast, one logcat line (tag QECastTiming) gives, for each step,
 * calls, average ms and maximum ms:
 * - frame: one whole encodeTexture call, and gap: the time between two calls' starts
 *   (1000 / gap is the most frames a second WebRTC offered the encoder);
 * - its parts: makeCurrent, clear (glClear), draw (drawOes), swap (swapBuffers into the
 *   encoder's input surface);
 * - out: dequeueOutputBuffer, collecting encoded frames;
 * - buf: encodeBuffer, if the byte-buffer path is used instead of the texture one;
 * - rate: the bitrate (kbps) and frame rate WebRTC last asked of the encoder, and how
 *   many times it asked (setRates). This follows WebRTC's bandwidth estimate.
 * initEncode's size, bitrate and frame rate are logged once per cast.
 */
object HorizonEncoderTiming {
    private const val TAG = "QECastTiming"
    private const val ENCODER = "org.webrtc.MediaCodecVideoEncoder"
    private const val EGL = "org.webrtc.EglBase14"
    private const val DRAWER = "org.webrtc.GlRectDrawer"
    private const val REPORT_MS = 3000L

    private class Stat(val name: String) {
        var n = 0
        var totalNs = 0L
        var maxNs = 0L

        fun add(ns: Long) {
            n++
            totalNs += ns
            if (ns > maxNs) maxNs = ns
        }

        fun text(): String {
            if (n == 0) return "$name 0"
            val avg = totalNs / n / 1e6
            return "$name $n %.1f/%.1f".format(avg, maxNs / 1e6)
        }

        fun reset() {
            n = 0; totalNs = 0; maxNs = 0
        }
    }

    private val frame = Stat("frame")
    private val gap = Stat("gap")
    private val makeCurrent = Stat("makeCurrent")
    private val clear = Stat("clear")
    private val draw = Stat("draw")
    private val swap = Stat("swap")
    private val out = Stat("out")
    private val buf = Stat("buf")
    private val all = listOf(frame, gap, makeCurrent, clear, draw, swap, out, buf)

    private val lock = Any()
    private var lastFrameStart = 0L
    private var windowStart = 0L

    // Set on the encoder thread while encodeTexture runs, so the EGL and drawer
    // timings count only the encoder's own calls.
    private val inEncode = ThreadLocal<Boolean>()

    // Last setRates(kbps, fps) and the number of calls since the last report.
    private var rateKbps = -1
    private var rateFps = -1
    private var rateCalls = 0

    fun install(cl: ClassLoader) {
        val encoder = XposedHelpers.findClass(ENCODER, cl)
        timeAll(encoder, "encodeTexture", frame, onlyInEncode = false, isFrame = true)
        timeAll(encoder, "encodeBuffer", buf, onlyInEncode = false)
        timeAll(encoder, "dequeueOutputBuffer", out, onlyInEncode = false)
        XposedBridge.hookAllMethods(encoder, "setRates", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val kbps = param.args[0] as Int
                val fps = param.args[1] as Int
                synchronized(lock) {
                    rateKbps = kbps; rateFps = fps; rateCalls++
                }
            }
        })
        XposedBridge.hookAllMethods(encoder, "initEncode", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                val a = param.args
                Log.i(TAG, "initEncode ${a[0]} ${a[1]}x${a[2]} ${a[3]} kbps ${a[4]} fps")
                synchronized(lock) {
                    rateKbps = a[3] as Int; rateFps = a[4] as Int; rateCalls = 0
                }
            }
        })
        timeAll(XposedHelpers.findClass(EGL, cl), "makeCurrent", makeCurrent, onlyInEncode = true)
        timeAll(XposedHelpers.findClass(EGL, cl), "swapBuffers", swap, onlyInEncode = true)
        timeAll(XposedHelpers.findClass(DRAWER, cl), "drawOes", draw, onlyInEncode = true)
        XposedHelpers.findAndHookMethod(
            "android.opengl.GLES20", cl, "glClear", Int::class.javaPrimitiveType,
            timer(clear, onlyInEncode = true, isFrame = false)
        )
        XposedBridge.log("QuestEnhancer/Cast: encoder timing on (logcat tag $TAG)")
    }

    private fun timeAll(
        cls: Class<*>, method: String, stat: Stat, onlyInEncode: Boolean, isFrame: Boolean = false
    ) {
        val hooks = XposedBridge.hookAllMethods(cls, method, timer(stat, onlyInEncode, isFrame))
        if (hooks.isEmpty()) XposedBridge.log("QuestEnhancer/Cast: no $method in ${cls.name}")
    }

    private fun timer(stat: Stat, onlyInEncode: Boolean, isFrame: Boolean) =
        object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (onlyInEncode && inEncode.get() != true) return
                val now = SystemClock.elapsedRealtimeNanos()
                if (isFrame) {
                    inEncode.set(true)
                    synchronized(lock) {
                        if (lastFrameStart != 0L) gap.add(now - lastFrameStart)
                        lastFrameStart = now
                    }
                }
                param.setObjectExtra("qe_t0", now)
            }

            override fun afterHookedMethod(param: MethodHookParam) {
                val t0 = param.getObjectExtra("qe_t0") as Long? ?: return
                val now = SystemClock.elapsedRealtimeNanos()
                if (isFrame) inEncode.set(false)
                synchronized(lock) {
                    stat.add(now - t0)
                    if (windowStart == 0L) windowStart = now
                    if (now - windowStart >= REPORT_MS * 1_000_000) {
                        Log.i(
                            TAG, all.joinToString(" | ") { it.text() } +
                                " | rate $rateKbps kbps $rateFps fps ($rateCalls calls)"
                        )
                        all.forEach { it.reset() }
                        rateCalls = 0
                        windowStart = now
                    }
                }
            }
        }
}
