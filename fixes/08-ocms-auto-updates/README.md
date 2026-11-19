# 08: Keep Meta's app auto-updates off

**What keeps updates off (keep all of these in mind):**

| # | Setting | Blocks | Where |
| - | ------- | ------ | ----- |
| 1 | `persist.ovr.ota.enabled=false` | OS updates (OSUpdater checks it before every check) | set by PrivateQuest setup |
| 2 | `com.oculus.updater` disabled (`pm disable-user --user 0`) | OS updates, second block | 2026-11-06 19:59 |
| 3 | Account "auto-update apps" (`auto_updates_enabled`) OFF | App updates, Meta's own apps included | Meta Horizon phone app |
| 4 | QuestEnhancer `OcmsUpdateHooks` (scope `com.oculus.ocms`) | App updates even if Meta turns 3 back on | this fix |

Gap: an OS update already downloaded before 1/2 were set still installs.

**Problem.** On 2026-11-06 at 19:39 OCMS (`com.oculus.ocms`) auto-updated Chats
(`com.oculus.socialplatform`) from the stock v155 to v295, which needs a class the
Quest 1 firmware doesn't have, and re-enabled it: a crash loop (see 02).

**What decides it** (OCMS 50.0.0.198.257 / 455911499, read in
`meta-apps/UPDATE-BLOCKING-RESEARCH.md`): OCMS's periodic auto-update asks
`com.oculus.autoupdates.AutoUpdateScheduler.A01(enterpriseMode)`. That needs OCMS to
hold `WRITE_EXTERNAL_STORAGE` and then reads the account's "auto-update apps" setting
(`auto_updates_enabled` in the Oculus Profile provider). Meta's own apps are not
skipped (that skip exists only in debug builds). There's no per-app skip list.
OS updates are separate and already off (`persist.ovr.ota.enabled=false`).

**Fix.**
1. In the Meta Horizon phone app, turn off auto-updating apps for the headset.
2. QuestEnhancer's `OcmsUpdateHooks` makes `A01` always answer false, so the
   account setting can't be turned back on from Meta's side. Manual Store
   installs and updates don't go through `A01`.
3. `apply.ps1` adds `com.oculus.ocms` to QuestEnhancer's Vector scope, enables
   OCMS again (it was disabled 2026-11-06 19:59, which also stops Store installs)
   and restarts it so the hook loads.

**Check.** Open the Store once (starts OCMS with the hook), run `06-questenhancer/qe-check.sh` and look for
`QuestEnhancer/OcmsUpdates: auto-updates blocked`.

**Test it any time** (with `adb -s` set to your headset):
1. Force OCMS's auto-update job (id 1828726819): `adb shell "su -c 'cmd jobscheduler run -f com.oculus.ocms 1828726819'"`
2. After ~10 s: `adb logcat -d | findstr OcmsUpdates` shows a new `auto-update run blocked` line.
3. Scope still has OCMS: `adb shell "su -c 'sh /data/adb/modules/zygisk_vector/cli scope ls org.codeberg.anonymous950.questenhancer'"`
Verified this way 2026-11-06 21:51:09. Re-check after updating/removing QuestEnhancer, changing Vector/Zygisk, or a factory reset.

**Not covered.** The Store app updates its own React Native code over the air.
The setup-time core-app installer (CoreAppManager) ignores the setting but is
dormant once setup is complete. Controller firmware is a separate updater.

**Undo.** `undo.ps1` takes `com.oculus.ocms` out of the scope and restarts OCMS
(OCMS stays enabled; disable it with `pm disable-user --user 0 com.oculus.ocms`
if you want no app installs at all).

**Status.** Installed 2026-11-06 21:49 (QuestEnhancer CI run 19, 9c0c34d). Log: `auto-updates blocked`, then `auto-update run blocked (OCMS would have allowed it)` 3 s after OCMS started. No crashes since.
