# 03: Android Developer options crash

**Problem.** Android's Developer options page crashes the moment it opens.
(The usual "developer settings" request opens Meta's settings instead; the
Android page is `com.android.settings/.Settings$DevelopmentSettingsDashboardActivity`.)

**Root cause.** The page's "Memory" entry (`MemoryUsagePreferenceController`)
reads `/proc/zoneinfo` through `Debug.getZoneWatermarks`. Meta's SELinux
policy does not let Settings (`system_app`) read it:

```
avc: denied { read } for comm="ndroid.settings" name="zoneinfo" scontext=u:r:system_app:s0 tcontext=u:object_r:proc_zoneinfo:s0
FATAL EXCEPTION: main ... java.lang.RuntimeException: SysMemInfo read failed
    at android.os.Debug.getZoneWatermarks(Native Method)
    at com.android.internal.util.MemInfoReader.readMemInfo(MemInfoReader.java:37)
    ... MemoryUsagePreferenceController.displayPreference(MemoryUsagePreferenceController.java:54)
```

The same code is also denied a search of `/sys/block/zram0`.

**Fix.** A Magisk module (`module/`) whose `sepolicy.rule` adds exactly those
permissions at every boot:

```
allow system_app proc_zoneinfo file { open read getattr }
allow system_app sysfs_zram dir search
allow system_app sysfs_zram file { open read getattr }
```

**Status.** Confirmed on the headset 2026-11-05: first live with
`magiskpolicy --live`, then with the module installed, after a reboot the
page opens and stays open.

**Apply.** CI packs `module/` into `03-devsettings-selinux.zip`. Put the zip
next to `apply.ps1` (or pass `-Zip`), then run `apply.ps1`: it pushes the zip,
installs it with `magisk --install-module`, reboots, and opens the page.

**Undo.** `undo.ps1` marks the module for removal; reboot to finish.
