package org.codeberg.anonymous950.questenhancer

import android.media.MediaCodec
import android.media.MediaFormat
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Test switch: caps the bitrate of Horizon's cast encoder.
 *
 * In a cast to the TV, WebRTC asks the encoder for 2000 kbps (CBR) while only about
 * 2 frames a second get through, so each frame is large (about 90 KB); WebRTC then
 * pauses the encoder until its send queue drains. A lower bitrate makes each frame
 * smaller; if the pauses are the send queue's, more frames should get through.
 *
 * Off unless the system property debug.qe.cast_kbps is above 0 (read at each call,
 * so setprop takes effect at the next cast, no reboot):
 *   su -c setprop debug.qe.cast_kbps 800
 * The cap only ever lowers the bitrate WebRTC asks for, both when the encoder is
 * configured and on each setRates.
 */
object HorizonEncoderBitrate {
    private const val TAG = "QuestEnhancer/Cast"
    private const val ENCODER = "org.webrtc.MediaCodecVideoEncoder"
    private const val PROP = "debug.qe.cast_kbps"

    fun install(cl: ClassLoader) {
        // setRates(int kbps, int fps): the Java side passes only kbps to the codec.
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(ENCODER, cl), "setRates",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val cap = cap()
                    val kbps = param.args[0] as Int
                    if (cap in 1 until kbps) {
                        param.args[0] = cap
                        android.util.Log.i("QECastTiming", "setRates $kbps kbps capped to $cap")
                    }
                }
            })

        XposedHelpers.findAndHookMethod(
            MediaCodec::class.java, "configure",
            MediaFormat::class.java, android.view.Surface::class.java,
            android.media.MediaCrypto::class.java, Int::class.javaPrimitiveType,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val format = param.args[0] as? MediaFormat ?: return
                    if ((param.args[3] as Int) and MediaCodec.CONFIGURE_FLAG_ENCODE == 0) return
                    if (format.getString(MediaFormat.KEY_MIME) != MediaFormat.MIMETYPE_VIDEO_AVC) return
                    val cap = cap()
                    if (cap <= 0 || !format.containsKey(MediaFormat.KEY_BIT_RATE)) return
                    val bps = format.getInteger(MediaFormat.KEY_BIT_RATE)
                    if (cap * 1000 < bps) {
                        format.setInteger(MediaFormat.KEY_BIT_RATE, cap * 1000)
                        XposedBridge.log("$TAG: encoder bitrate capped ${bps / 1000} -> $cap kbps")
                    }
                }
            })
    }

    private fun cap(): Int = try {
        val sp = Class.forName("android.os.SystemProperties")
        (sp.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, PROP, 0) as Int)
    } catch (t: Throwable) {
        0
    }
}
