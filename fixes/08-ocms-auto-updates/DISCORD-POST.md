Draft for the FreeXR Discord.

## Short version (one message)

**[v50] Blocking Meta app auto-updates on a rooted Quest 1**
OCMS (Meta's app manager) auto-updated Chats to v295, which needs a class v50 doesn't have, so it crash-looped. OS-update blocking (PQ OTA toggle / disabling `com.oculus.updater`) doesn't cover app updates.
Disabling OCMS stops the updates, but SystemUX reads its app list from OCMS, so then you get "SystemUX keeps stopping". **Don't disable OCMS.**
**Finding:** every OCMS auto-update run goes through `AutoUpdateScheduler.A01()`. That only checks your account's "auto-update apps" setting. Meta's own apps aren't skipped, and there's no per-app skip list.
**Patch:** a QuestEnhancer (Vector) hook that makes `A01` return false. Store installs and manual updates still work.
Install the build, add `com.oculus.ocms` to its scope, and keep OCMS enabled.
**Tested:** I forced the update job (`cmd jobscheduler run -f com.oculus.ocms 1828726819`). The log shows `OcmsUpdates: auto-update run blocked`, and none of the 61 Meta app versions changed.
Build: https://github.com/Bubbagum88/QuestEnhancer

## Long version (two messages, each under 2000 characters)

---

**[v50] Stopping Meta app auto-updates on a rooted Quest 1 (without breaking SystemUX)**

**What happened.** On 2026-11-06 OCMS (`com.oculus.ocms`, Meta's app manager) auto-updated Chats (`com.oculus.socialplatform`) from the stock v155 to v295. v295 needs a framework class that v50 doesn't have, so it crash-looped and kept restarting Horizon. Blocking OS updates (`persist.ovr.ota.enabled=false` from PQ, `pm disable-user com.oculus.updater`) doesn't cover this, because app updates are a separate system.

**What didn't work.** `pm disable-user --user 0 com.oculus.ocms` does stop app updates. But SystemUX reads its app list from OCMS (`content://com.oculus.ocms.library`) at startup, so "SystemUX keeps stopping" (`IllegalArgumentException: Unknown authority com.oculus.ocms.library` in `SystemUXApplication.initEntitlements`). **Don't disable OCMS.**

**What we found** (decompiled OCMS 455911499 / v50):
- The periodic auto-update job asks `com.oculus.autoupdates.AutoUpdateScheduler.A01()`. It returns true only if OCMS has `WRITE_EXTERNAL_STORAGE` and your account's "auto-update apps" setting is on.
- Meta's own apps get no exemption. The "skip system apps" check only exists in debug builds.
- There's no per-app skip list. The per-app `auto_update_enabled` flag isn't read anywhere in this code path.
- The setup-time core-app installer ignores the setting, but it does nothing once setup is complete.

---

**The patch.** I added a hook to QuestEnhancer (Xposed/Vector) that makes `AutoUpdateScheduler.A01` always return false. Store installs and manual updates still work, because they don't go through A01.
Build: https://github.com/Bubbagum88/QuestEnhancer (OCMS: keep Meta app auto-updates off)

1. Install that QuestEnhancer build and add `com.oculus.ocms` to its scope in Vector, keeping your other scoped apps.
2. Re-enable OCMS if you disabled it: `adb shell su -c 'pm enable com.oculus.ocms'`, then `am force-stop com.oculus.ocms`
3. Optional: turn off "auto-update apps" for the headset in the Meta Horizon phone app as a second layer.

**Verify.** Force the auto-update job to run, then check the log:
`adb shell su -c 'cmd jobscheduler run -f com.oculus.ocms 1828726819'`
`adb logcat -d | grep OcmsUpdates` → `auto-update run blocked (OCMS would have allowed it)`
Tested on one Quest 1 (v50, Magisk + Vector): the forced run was blocked, no app updated afterwards, and SystemUX is stable.

**Limits.** This doesn't block OS OTA, so keep `persist.ovr.ota.enabled=false` and the updater disabled. It also doesn't stop the Store's own over-the-air JS updates or controller firmware updates. A factory reset wipes all of it.
