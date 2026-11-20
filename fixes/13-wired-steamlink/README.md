# 13: Wired Steam Link (USB networking)

[misteranderson91's q1_steamlink_usb v1.3](https://github.com/misteranderson91/quest1-steamlink-patcher/tree/main/magisk-module)
(MIT, licence in `module/LICENSE`), unchanged. Module id `q1_steamlink_usb`.

The Quest 1's USB HAL refuses USB networking (NCM), but its kernel has it. At boot
the module adds the NCM function next to adb/Link in the USB gadget, gives the
headset `10.86.13.37/29` on `usb0`, hands the PC `10.86.13.38` by DHCP (no gateway,
so the PC never routes internet through the headset) and lets apps route to that
subnet. Steam Link then streams over the cable, with Wi-Fi as a backup. No system
files are changed; removing the module is a full revert. Log:
`/data/adb/q1_steamlink_usb.log`.

It only installs on a Quest 1 (`monterey`) whose kernel has `ncm.0`. If the USB HAL
keeps removing NCM, it re-adds it at most 5 times, so adb over USB can't be knocked
off forever.

## You also need

The patched Steam Link app: Valve's Steam Link doesn't run on the Quest 1 as is.
Patch the copy from your own SteamVR install with
[the patcher's web page](https://misteranderson91.github.io/quest1-steamlink-patcher/)
or its `quest1_patcher.py`. QuestEnhancer doesn't ship it. Wireless streaming
works with the patched app alone; this module is only for the cable.

## Coming

Bluedragon in the Monterey Club Discord is working on a hand tracking patch for
Quest 1 Steam Link (2.1, a 21 Hz pose model). It isn't released yet; it'll be
linked here when it is.

## Install

Flash `13-wired-steamlink-rN.zip` (CI artifact `quest1-fixes-rN-<commit>`) in Magisk,
restart. Not yet tested on our headset: check adb over USB still works after it.
