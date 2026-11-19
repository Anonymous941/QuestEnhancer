package org.codeberg.anonymous950.questenhancer

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Chromecast casting (com.oculus.vrcast, VrCastService v10).
 *
 * vrcast answers WebRTC's offer for the TV itself (GoogleCastMWSBridge.jsonAnswerToSdp).
 * Two problems were seen casting to a Vizio TV:
 *
 * 1. Dropped feedback. The TV has two network interfaces up (e.g. Ethernet and Wi-Fi).
 *    vrcast writes the control-channel address into the answer's "c=IN IP4 <ip>" video
 *    line, but the TV sends its RTP/RTCP from the OTHER interface. WebRTC takes the c=
 *    line as its one remote address, so every packet from the TV is rejected ("Received
 *    non-STUN packet from unknown address"). With no RTCP feedback the bandwidth estimate
 *    never grows, the pacer stays near its ~750 kbps start, and frames queue up (lag).
 *    debug.qe.gcast_remote_ip rewrites that video c= line to the given IP, so WebRTC's
 *    remote address matches where the TV actually sends from:
 *      su -c setprop debug.qe.gcast_remote_ip 192.168.1.89
 *    Empty (default) leaves the address as vrcast set it.
 *
 * 2. No start/min bitrate. The answer has only "a=fmtp:96 x-google-max-bitrate=N", so
 *    WebRTC starts low. debug.qe.gcast_min_kbps / debug.qe.gcast_start_kbps add
 *    x-google-min-bitrate / x-google-start-bitrate to that line. (On its own this did not
 *    help while the feedback was being dropped; use it once the address is fixed.)
 *      su -c setprop debug.qe.gcast_min_kbps 2000
 *      su -c setprop debug.qe.gcast_start_kbps 2500
 *
 * All off by default; the properties are read at each cast (no reboot). vrcast's own
 * debug.oculus.gcast.bitrate (default 3000) stays the maximum.
 */
object VrCastHooks {
    private const val TAG = "QuestEnhancer/Cast"
    private const val BRIDGE = "com.oculus.vrcast.googlecast.GoogleCastMWSBridge"
    private const val FMTP = "a=fmtp:96 "

    // A video connection line in the answer SDP: "c=IN IP4 <ip>" that is not 0.0.0.0.
    private val C_LINE = Regex("""c=IN IP4 (?!0\.0\.0\.0)(\d{1,3}(?:\.\d{1,3}){3})""")

    fun install(cl: ClassLoader) {
        val hooks = XposedBridge.hookAllMethods(
            XposedHelpers.findClass(BRIDGE, cl), "jsonAnswerToSdp",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    var sdp = param.result as? String ?: return
                    var changed = false

                    val ip = propStr("debug.qe.gcast_remote_ip")
                    if (ip.isNotEmpty()) {
                        val m = C_LINE.find(sdp)
                        if (m == null) {
                            XposedBridge.log("$TAG: answer has no rewritable c= line; IP left as is")
                        } else if (m.groupValues[1] != ip) {
                            sdp = C_LINE.replaceFirst(sdp, "c=IN IP4 $ip")
                            XposedBridge.log("$TAG: answer video address ${m.groupValues[1]} -> $ip")
                            changed = true
                        }
                    }

                    val min = prop("debug.qe.gcast_min_kbps")
                    val start = prop("debug.qe.gcast_start_kbps")
                    if (min > 0 || start > 0) {
                        if (sdp.contains(FMTP)) {
                            var add = ""
                            if (min > 0) add += "x-google-min-bitrate=$min;"
                            if (start > 0) add += "x-google-start-bitrate=$start;"
                            sdp = sdp.replace(FMTP, FMTP + add)
                            XposedBridge.log("$TAG: answer fmtp now ${add}x-google-max-bitrate (min $min, start $start)")
                            changed = true
                        } else {
                            XposedBridge.log("$TAG: answer has no $FMTP line; bitrate left as is")
                        }
                    }

                    if (changed) param.result = sdp
                }
            })
        XposedBridge.log("$TAG: vrcast answer hook: ${hooks.size} method(s)")
    }

    private fun prop(name: String): Int = try {
        val sp = Class.forName("android.os.SystemProperties")
        sp.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
            .invoke(null, name, 0) as Int
    } catch (t: Throwable) {
        0
    }

    private fun propStr(name: String): String = try {
        val sp = Class.forName("android.os.SystemProperties")
        (sp.getMethod("get", String::class.java).invoke(null, name) as? String ?: "").trim()
    } catch (t: Throwable) {
        ""
    }
}
