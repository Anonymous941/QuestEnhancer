package org.codeberg.anonymous950.questenhancer

import android.media.MediaCodec
import android.media.MediaFormat
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Optional: asks the H.264 encoder Horizon casts with to run as a real-time encoder.
 *
 * WebRTC configures it with size, bitrate, frame rate and colour format only. Android's
 * MediaFormat also has "priority" (0 = real time) and "operating-rate" (the frames a
 * second the codec must be able to sustain), which the Qualcomm encoder uses to pick
 * its clock; without them it may run at a low clock.
 *
 * Off unless the system property debug.qe.cast_realtime is 1, so the same build casts
 * with and without it (setprop takes effect at the next cast, no reboot):
 *   su -c setprop debug.qe.cast_realtime 1
 * If the encoder refuses the extra keys, it is configured again exactly as WebRTC asked.
 */
object HorizonEncoderPriority {
    private const val TAG = "QuestEnhancer/Cast"
    private const val PROP = "debug.qe.cast_realtime"
    private const val OPERATING_RATE = 60

    fun install() {
        XposedHelpers.findAndHookMethod(
            MediaCodec::class.java, "configure",
            MediaFormat::class.java, android.view.Surface::class.java,
            android.media.MediaCrypto::class.java, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val format = param.args[0] as? MediaFormat ?: return
                    val flags = param.args[3] as Int
                    if (flags and MediaCodec.CONFIGURE_FLAG_ENCODE == 0) return
                    if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_VIDEO_AVC) return
                    // MediaFormat.removeKey, needed to undo the keys, is API 29.
                    if (android.os.Build.VERSION.SDK_INT < 29) return
                    val on = enabled()
                    XposedBridge.log("$TAG: encoder configure $format; $PROP=$on")
                    if (!on) return
                    format.setInteger(MediaFormat.KEY_PRIORITY, 0)
                    format.setInteger(MediaFormat.KEY_OPERATING_RATE, OPERATING_RATE)
                    param.setObjectExtra("qe_added", true)
                }

                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.getObjectExtra("qe_added") != true) return
                    val error = param.throwable
                    if (error == null) {
                        XposedBridge.log("$TAG: encoder accepted priority 0, operating-rate $OPERATING_RATE")
                        return
                    }
                    XposedBridge.log("$TAG: encoder refused the real-time keys ($error); configuring as asked")
                    val format = param.args[0] as MediaFormat
                    @Suppress("NewApi")
                    format.removeKey(MediaFormat.KEY_PRIORITY)
                    @Suppress("NewApi")
                    format.removeKey(MediaFormat.KEY_OPERATING_RATE)
                    try {
                        XposedBridge.invokeOriginalMethod(param.method, param.thisObject, param.args)
                        param.result = null
                    } catch (t: java.lang.reflect.InvocationTargetException) {
                        param.throwable = t.targetException
                    }
                }
            })
    }

    private fun enabled(): Boolean = try {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("get", String::class.java, String::class.java)
            .invoke(null, PROP, "0") == "1"
    } catch (t: Throwable) {
        false
    }
}
