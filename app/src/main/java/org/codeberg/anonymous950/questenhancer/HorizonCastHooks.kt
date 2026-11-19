package org.codeberg.anonymous950.questenhancer

import android.content.Context
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import java.io.File

/**
 * Casting diagnostics for Horizon (com.oculus.horizon, v50.0.0.198.257).
 *
 * Casting to a Chromecast TV sends 2-7 frames a second: the screen capture feeds
 * WebRTC 24 frames a second, but only about one in five reaches the video encoder.
 * WebRTC (liboculus_p2p.so) drops them in one of two places, and logs neither:
 * the encoder pause (send queue too long, or network down) or the frame dropper.
 *
 * This turns on WebRTC's own diagnostics, changing nothing else:
 * 1. its detailed log, to logcat (tags libjingle / WebRTC);
 * 2. its trace events during each cast, written when the cast stops to
 *    /data/data/com.oculus.horizon/files/webrtc-cast-trace.json. An "EncoderPaused"
 *    event there means the encoder pause is what drops the frames.
 *
 * The tracer is in org.webrtc.PeerConnectionFactory, whose static initializer loads
 * libjingle_peerconnection_so.so. Horizon has no such file (its WebRTC is
 * liboculus_p2p.so, loaded from inside Horizon.apk); the quest1_horizon_webrtc_lib
 * Magisk module adds an empty one. Without that module the tracer reports a failure and the
 * logging still works.
 */
object HorizonCastHooks {
    private const val TAG = "QuestEnhancer/Cast"
    private const val PLATFORM_PLUGIN = "com.oculus.horizon.platformplugin.PlatformPluginManager"
    private const val LOGGING = "org.webrtc.Logging"
    private const val FACTORY = "org.webrtc.PeerConnectionFactory"
    private const val TRACE_FILE = "webrtc-cast-trace.json"

    // webrtc::TraceLevel: state info, warning, error, critical, debug, info.
    private const val TRACE_LEVELS = 0x0001 or 0x0002 or 0x0004 or 0x0008 or 0x0800 or 0x1000

    // rtc::LoggingSeverity LS_VERBOSE (org.webrtc.Logging.Severity ordinal).
    private const val LS_VERBOSE = 1

    @Volatile
    private var filesDir: File? = null

    @Volatile
    private var loggingOn = false

    @Volatile
    private var tracing = false

    fun install(cl: ClassLoader) {
        hook("webrtc logging") { hookLibraryLoad(cl) }
        hook("cast trace") { hookCastTrace(cl) }
        hook("encoder timing") { HorizonEncoderTiming.install(cl) }
        hook("encoder priority") { HorizonEncoderPriority.install() }
        hook("encoder bitrate cap") { HorizonEncoderBitrate.install(cl) }
    }

    private fun hook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: $name not installed: $t")
        }
    }

    // PlatformPluginManager's constructor loads libovrplatformplugin.so, which needs
    // liboculus_p2p.so; WebRTC's JNI methods work from then on.
    private fun hookLibraryLoad(cl: ClassLoader) {
        XposedBridge.hookAllConstructors(
            XposedHelpers.findClass(PLATFORM_PLUGIN, cl),
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (loggingOn) return
                    filesDir = (param.args[0] as Context).filesDir
                    try {
                        val logging = XposedHelpers.findClass(LOGGING, cl)
                        XposedHelpers.callStaticMethod(
                            logging, "nativeEnableTracing", "logcat:", TRACE_LEVELS, LS_VERBOSE
                        )
                        XposedBridge.log("$TAG: WebRTC detailed logging on")
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG: WebRTC logging failed: $t")
                    }
                    try {
                        XposedHelpers.callStaticMethod(
                            XposedHelpers.findClass(FACTORY, cl), "initializeInternalTracer"
                        )
                        loggingOn = true
                        XposedBridge.log("$TAG: WebRTC tracer ready")
                    } catch (t: Throwable) {
                        XposedBridge.log("$TAG: WebRTC tracer failed: $t")
                    }
                }
            })
    }

    // Trace events from the start of a cast to its end. A Chromecast cast starts with
    // nativeInitializeForCastingNativeReceiver (width, height); other casts with
    // nativeInitializeForCastingWithTurnServers (height, width).
    private fun hookCastTrace(cl: ClassLoader) {
        val int = Int::class.javaPrimitiveType
        val str = String::class.java
        val factory = XposedHelpers.findClass(FACTORY, cl)

        fun startTrace(kind: String, width: Any?, height: Any?, fps: Any?, kbps: Any?) {
            val dir = filesDir ?: return
            if (!loggingOn || tracing) return
            val file = File(dir, TRACE_FILE).path
            tracing = XposedHelpers.callStaticMethod(
                factory, "startInternalTracingCapture", file
            ) as Boolean
            XposedBridge.log(
                "$TAG: $kind cast ${width}x$height $fps fps $kbps kbps; trace to $file: $tracing"
            )
        }

        XposedHelpers.findAndHookMethod(
            PLATFORM_PLUGIN, cl, "nativeInitializeForCastingNativeReceiver",
            str, str, str, int, int, int, int,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val a = param.args
                    startTrace("Chromecast", a[3], a[4], a[5], a[6])
                }
            })

        XposedHelpers.findAndHookMethod(
            PLATFORM_PLUGIN, cl, "nativeInitializeForCastingWithTurnServers",
            str, str, str, int, int, int, int,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val a = param.args
                    startTrace("TURN", a[4], a[3], a[5], a[6])
                }
            })

        XposedHelpers.findAndHookMethod(
            PLATFORM_PLUGIN, cl, "nativeStopLiveStreaming",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!tracing) return
                    XposedHelpers.callStaticMethod(factory, "stopInternalTracingCapture")
                    tracing = false
                    XposedBridge.log("$TAG: cast trace written")
                }
            })
    }
}
