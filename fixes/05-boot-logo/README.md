# 05: Boot logo

Replaces the logo in the boot animation. `vr_bootanimation` (in /system/bin)
draws `/system/etc/meta_logo.png`; this Magisk module overlays that file with our
own `module/system/etc/meta_logo.png`. Same idea as ethan52's community "Meta boot
logo changer" (Discord, 2026-09-29), with our own picture.

- Stock file (pulled 2026-11-06, v50 build 49845030443200410): 216x144 RGB PNG,
  logo on black. Your logos must keep that size and format.
- No logos are included here: put your own pictures in `module/logos/`
  before building the module or the boot image.
- Not changed: the Meta logo in the earliest boot stage (about 2 s). See
  "Where the first Meta logo comes from" below: it is inside XBL and can't be changed.

## Random logos

`module/logos/` holds the logos (216x144 RGB PNG, on black; empty in this
repository, add your own). After each boot,
`service.sh` runs `pick.sh`, which copies a random one (not the one already in
place) into `system/etc/meta_logo.png`, for the next time the animation plays.
The choice is logged in `/data/adb/modules/quest1_boot_logo/last-pick.txt`.

By hand:

    .\adb shell "su -c 'sh /data/adb/modules/quest1_boot_logo/pick.sh list'"
    .\adb shell "su -c 'sh /data/adb/modules/quest1_boot_logo/pick.sh NAME'"

To add a logo, put a 216x144 RGB PNG on black in `module/logos/` (or in
`/data/adb/modules/quest1_boot_logo/logos/` on the headset).

## Why it doesn't show on a normal boot (checked 2026-11-06)

`init.monterey.rc` starts `bootanim` at `late-fs`, before Magisk mounts modules at
`post-fs-data`, so on a cold boot vr_bootanimation has already read the stock file.
The overlay itself works: after install, `md5sum /system/etc/meta_logo.png` matched
our file. vr_bootanimation reads `/system/etc/meta_logo-L.png` and `-R.png`
(per eye, absent on our headset) and `/system/etc/meta_logo.png`. A soft restart
(`.\adb shell "su -c 'stop; start'"`) replays the animation with our logo. Showing
it on a cold boot needs the file in the boot image: see "Boot image method" below.

## Apply / undo

From platform-tools in PowerShell, with the zip (from CI, or the one on the
desktop) next to the script:

    .\apply.ps1      # installs the module and reboots
    .\undo.ps1       # removes it and reboots

Status: v2 installed 2026-11-06; confirmed on headset: pick.sh n64 + soft restart showed the N64 logo. v3 fixes the random pick (not yet installed). The module is now disabled on our headset: the boot image method below replaced it, and its `logos/` folder is the source for that build.

## Boot image method (cold boot, used on our headset)

`boot-image/build-boot-image.sh` adds every logo, a small `q1pick.sh` and an rc file to
the Magisk boot image's ramdisk (`overlay.d`). At `post-fs`, before `late-fs` starts
bootanim, init runs `q1pick.sh` in Magisk's SELinux domain and it bind-mounts a random
logo over `/system/etc/meta_logo.png`. (An rc `mount` from init itself is denied:
`avc: denied { mounton } scontext=u:r:init:s0 tcontext=u:object_r:system_file:s0`.)
The pick is in the kernel log: `dmesg | grep q1logo`.

Back up both boot slots first (`dd` of `boot_a`/`boot_b` from
`/dev/block/bootdevice/by-name/` to /sdcard, then pull them). Then, from platform-tools:

    .\adb shell "su -c 'dd if=/dev/block/bootdevice/by-name/boot_a of=/sdcard/Download/boot_a.img'"
    .\adb push boot-image\build-boot-image.sh /data/local/tmp/
    .\adb shell "su -c 'sh /data/local/tmp/build-boot-image.sh'"
    # logos are read from /data/adb/modules/quest1_boot_logo/logos/ (this fix's module);
    # without the module, use any folder: su -c 'LOGOS=/sdcard/logos sh build-boot-image.sh'
    # optional RAM-only test over USB: .\adb reboot bootloader, then
    #   .\fastboot boot boot_a_logo.img (after pulling it)
    .\adb shell "su -c 'dd if=/sdcard/Download/boot_a_logo.img of=/dev/block/bootdevice/by-name/boot_a bs=4M; sync; sha256sum /sdcard/Download/boot_a_logo.img /dev/block/bootdevice/by-name/boot_a'"

The two sha256 lines must match before rebooting. Undo: the same `dd` with your
`boot_a.img` backup, or `.\fastboot flash boot_a boot_a.img` if it won't boot.

Reinstalling or updating Magisk rewrites boot_a and drops the logos: dump the new
boot_a and run the script again.

Status: flashed to boot_a 2026-11-06 01:24 (v50 build 49845030443200410, Magisk 30.7).
Confirmed on headset: cold boots show the Meta logo (XBL, below), then a random logo
(Honda, Apple) for the whole animation. Game Boy logo redrawn on black (2026-11-06 01:51):
every logo must be on black, like the stock one.

## Where the first Meta logo comes from (checked 2026-11-06)

- `splash` partition (sde33, 33 MB): a `DDPH` v1 header and two 8-bit BMPs of the old
  Oculus ring (1920x1600 at 0x4000, 2880x1600 at 0xa00000), no signature. We blanked
  both: no visible change. XBL reads it only for some cases ("Failed to read splash
  image header from partition").
- `abl_a`: no image; it keeps the screen XBL drew ("Backup the boot logo blt buffer").
- `xbl_a`: its gzip-compressed UEFI volume (GUID section 1d301fe9-...) holds a 1412x148
  24-bit BMP with the Meta logo once per eye. That is the first logo. XBL is signed by
  secure boot: editing it bricks the headset with no fastboot to recover. Leave it.

