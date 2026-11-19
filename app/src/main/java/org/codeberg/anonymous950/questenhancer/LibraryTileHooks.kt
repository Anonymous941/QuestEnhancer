package org.codeberg.anonymous950.questenhancer

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers

/**
 * Library tiles (Quest 1, v50 SystemUX), set from the QuestEnhancer Manager with
 * system properties, read again on every Library refresh:
 *
 * 1. `persist.qe.library_hide`: Meta's built-in tiles to leave out, a comma list of
 *    PolicyApp names (EVENTS, DOGFOOD_HUB, FILE_MANAGER, FILE_VIEWER,
 *    FIT_AND_FOCUS_TUTORIAL, HANDS_TUTORIAL, SETTINGS, SHARE, PEOPLE, MESSENGER).
 *    LibraryFakeAppUtils.getFakeAppsForLibrary builds one tile per name in the list
 *    it is given (SystemUXConfiguration.defaultApps); we hand it the list without them.
 * 2. `persist.qe.library_sideloads=1`: sideloaded apps also in the All list, under an
 *    "Unknown Sources" heading, before the Get more apps button and the footer tip.
 *    No Meta flag does this: LibraryViewModel.getItems() only shows them in the
 *    Unknown Sources filter (and the launch-together page). Meta's own
 *    LibraryCopresenceHelper.addUnknownSourcesToItems builds the heading and tiles.
 */
object LibraryTileHooks {
    private const val TAG = "QuestEnhancer/Library"
    private const val HIDE_PROP = "persist.qe.library_hide"
    private const val SIDELOADS_PROP = "persist.qe.library_sideloads"

    private const val FAKE_APP_UTILS = "com.oculus.panelapp.library.utils.LibraryFakeAppUtils"
    private const val LIBRARY_VM = "com.oculus.panelapp.library.LibraryViewModel"

    // Items the sideloads go in front of, if the list has them.
    private val TAIL_TYPES = setOf("GET_MORE_APPS", "APP_FOOTER", "NULL_STATE")

    fun install(cl: ClassLoader) {
        hook("hide tiles") { hookHideTiles(cl) }
        hook("sideloads in All") { hookSideloadsInAll(cl) }
    }

    private fun hook(name: String, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedBridge.log("$TAG: $name not installed: $t")
        }
    }

    private fun prop(key: String): String = try {
        XposedHelpers.callStaticMethod(
            Class.forName("android.os.SystemProperties"), "get", key, ""
        ) as String
    } catch (t: Throwable) {
        ""
    }

    private fun hookHideTiles(cl: ClassLoader) {
        XposedHelpers.findAndHookMethod(
            FAKE_APP_UTILS, cl, "getFakeAppsForLibrary",
            android.content.Context::class.java, List::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val hide = prop(HIDE_PROP).split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                    if (hide.isEmpty()) return
                    val apps = param.args[1] as? List<*> ?: return
                    // A new list: defaultApps belongs to SystemUXConfiguration, never change it.
                    param.args[1] = apps.filter { it.toString() !in hide }
                }
            })
    }

    private fun hookSideloadsInAll(cl: ClassLoader) {
        XposedHelpers.findAndHookMethod(LIBRARY_VM, cl, "getItems", object : XC_MethodHook() {
            override fun afterHookedMethod(param: MethodHookParam) {
                if (prop(SIDELOADS_PROP) != "1") return
                val vm = param.thisObject
                if (XposedHelpers.getObjectField(vm, "currentFilter")?.toString() != "ALL") return
                if (XposedHelpers.callMethod(vm, "isInSubsection") as Boolean) return
                val orchestrator = XposedHelpers.getObjectField(vm, "libraryAppFetcherOrchestrator")
                if (!(XposedHelpers.callMethod(orchestrator, "isUnknownSourcesInitialized") as Boolean)) return
                val sideloads = XposedHelpers.getObjectField(vm, "unknownSources") as? List<*> ?: return
                if (sideloads.isEmpty()) return
                @Suppress("UNCHECKED_CAST")
                val items = param.result as? MutableList<Any?> ?: return
                // Not while the Library is still loading: the list is just a loading view.
                if (items.any { XposedHelpers.callMethod(it, "getItemType").toString() == "LOADING_VIEW" }) return

                // Meta's helper adds the heading and one tile per sideload to a plain list;
                // move them in front of the button/footer at the end.
                val extra = ArrayList<Any?>()
                val helper = XposedHelpers.getObjectField(vm, "libraryCopresenceHelper")
                XposedHelpers.callMethod(helper, "addUnknownSourcesToItems", extra, ArrayList(sideloads))
                var at = items.size
                while (at > 0 && XposedHelpers.callMethod(items[at - 1], "getItemType").toString() in TAIL_TYPES) at--
                items.addAll(at, extra)
            }
        })
    }
}
