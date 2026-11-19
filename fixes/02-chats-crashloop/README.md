# 02: "Chats keeps crashing" loop

**Problem.** A "Chats keeps crashing" dialog every 2 to 3 seconds,
interrupting Guardian setup.

**Root cause.** `com.oculus.socialplatform` (Chats) crashes with
`NoClassDefFoundError: horizonos.os.preferences.PreferencesManager`, a class
from newer Horizon OS builds that the Quest 1's last firmware does not have
(seen in `adb logcat -b crash`, 2026-10-26).

**Fix.** Disable the app for user 0: `pm disable-user --user 0
com.oculus.socialplatform`.

**Do not uninstall it.** `pm uninstall --user 0` was tried once and made
`com.oculus.presence` crash-loop instead (it looks the package up by name).
If it was uninstalled, `apply.ps1` puts it back with
`cmd package install-existing` before disabling it.

**Status.** Disabled on the headset since 2026-10-26 (the 2026-11-05 app list
shows it disabled, and no Chats loop since).

**Undo.** `undo.ps1` enables it again (the crash loop comes back).
