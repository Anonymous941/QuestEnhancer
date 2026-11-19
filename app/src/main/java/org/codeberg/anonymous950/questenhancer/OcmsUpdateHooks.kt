package org.codeberg.anonymous950.questenhancer

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Keeps Meta's app auto-updates off (Quest 1, OCMS 50.0.0.198.257 / 455911499).
 *
 * OCMS's periodic auto-update (AutoUpdatesManager) asks
 * AutoUpdateScheduler.A01(enterpriseMode) whether updates are allowed. A01 checks
 * that OCMS holds WRITE_EXTERNAL_STORAGE and then reads the account's "auto-update
 * apps" setting (Profile provider column auto_updates_enabled). A false answer
 * makes the run stop with PERMISSION_OR_SETTINGS before any app is looked at,
 * Meta's own apps (Chats / com.oculus.socialplatform) included.
 *
 * This answers false always, so the account setting can't be turned back on from
 * Meta's side. Manual installs and updates from the Store don't go through A01.
 *
 * Undo: remove com.oculus.ocms from this module's scope and restart OCMS.
 */
object OcmsUpdateHooks {
    private const val TAG = "QuestEnhancer/OcmsUpdates"
    private const val SCHEDULER = "com.oculus.autoupdates.AutoUpdateScheduler"

    fun install(classLoader: ClassLoader) {
        val scheduler = XposedHelpers.findClassIfExists(SCHEDULER, classLoader)
        if (scheduler == null) {
            XposedBridge.log("$TAG: $SCHEDULER not found, auto-updates NOT blocked (different OCMS build?)")
            return
        }
        // A01(boolean) -> boolean. Obfuscated name, so check the shape before hooking.
        val method = scheduler.declaredMethods.firstOrNull {
            it.name == "A01" &&
                it.returnType == Boolean::class.javaPrimitiveType &&
                it.parameterTypes.contentEquals(arrayOf(Boolean::class.javaPrimitiveType))
        }
        if (method == null) {
            XposedBridge.log("$TAG: $SCHEDULER.A01(boolean) not found, auto-updates NOT blocked (different OCMS build?)")
            return
        }
        XposedBridge.hookMethod(method, object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (param.result == true) {
                    XposedBridge.log("$TAG: auto-update run blocked (OCMS would have allowed it)")
                }
                param.result = false
            }
        })
        XposedBridge.log("$TAG: auto-updates blocked")
    }
}
