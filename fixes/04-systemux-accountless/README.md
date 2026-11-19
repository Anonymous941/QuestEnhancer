# 04: systemux-accountless (community module)

Source: [LucentW/systemux-accountless](https://github.com/LucentW/systemux-accountless),
commit `7e2a519`, made for the Quest 1 on v50. Its repo has no licence file,
so we don't copy its script here: `build.sh` fetches the pinned commit.

What it changes in `/system/priv-app/SystemUX/SystemUX.apk` (systemless, through Magisk):

- Dock: turns on the hidden bar-customization flags, lets any app (sideloaded too)
  be pinned from the Library menu, raises the pin limit from 5 to 10, and stops
  forcing Explore, Store, People and Share onto the bar.
- Library without a Meta account: when Meta's app service returns nothing, the
  Library, app permissions, App Lock, notifications and storage pages list the
  locally installed apps instead.

Not changed: the 3-window multitasking limit (it's in VrShell's native code).

## Our headset

- `pm path com.oculus.systemux` = `/system/priv-app/SystemUX/SystemUX.apk` (no /data update), checked 2026-11-06.
- SystemUX.apk sha256 `be425390...36c9ed` (29,794,854 bytes, build 49845030443200410).
- Built 2026-11-06 with smali/baksmali 2.5.2: all 10 patch points matched exactly once.
  Module zip sha256 `a4a520d8b1251565fd34e43dacc3b6f6e247dfe41332a1d93633f315efa66e0a`.

The zip contains Meta's patched APK, so it is not committed or built in CI.
Build it locally (`sh build.sh SystemUX.apk`, Linux or WSL).

## Apply / undo

From platform-tools in PowerShell, with the zip next to the script:

    .\apply.ps1      # checks the APK hash, installs the module, reboots
    .\undo.ps1       # marks the module for removal, reboots

Manual undo: `.\adb shell su -c "touch /data/adb/modules/systemux_dock/remove"` then reboot.
If the shell won't start, adb still works on 5555.

## Risk notes

- The patched APK keeps its original v1 signature. Android 10 only checks the
  AndroidManifest.xml digest for apps on the system partition, and that entry
  is untouched (the upstream author's claim; confirm on first boot).
- The module replaces the whole SystemUX folder, so the stock `oat/` (precompiled
  code) is dropped and the patched APK is compiled again on boot. Expect a slower first boot.

Status: built; not yet installed on the headset.
