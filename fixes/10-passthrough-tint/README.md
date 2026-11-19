# 10: Passthrough colour tint

[Juspertinry's q1_ptcolor v0.2.0](https://github.com/Juspertinry/q1-modules/tree/main/q1_ptcolor)
(MIT, licence in `module/LICENSE`), unchanged. Module id `questptcolor`.

Quest 1 passthrough is black and white. This module rewrites, in a private copy of
`MrServiceMonterey.apk` that Magisk mounts over the stock one, the return line of
mrservice's `samplePassthroughTextures()` shader so the grey is multiplied by a colour.
Only passthrough changes: menus, apps and video don't. `/system` is never written,
so uninstalling the module is a full revert.

Safety, from its code: it refuses any build but `monterey` /
`49845030443200410` (Quest 1 v50), and checks a hash of each shader window before
writing. On another build it installs but does nothing.

Don't use the "Quest 2 Passthrough Colorizer" repack shared on Discord
(`mr_colour_ks.zip`, author "Juspertinry (welp, kinda uzi)") on a Quest 1: it points
at `MrServiceHollywood.apk` and has the build check commented out.

## Install

Flash `10-passthrough-tint.zip` in Magisk, restart. Default tint is ice blue.

## Use

The QuestEnhancer Manager's Passthrough page, or:

    su -c 'ptcolor'                   # settings and the live tint
    su -c 'ptcolor preset amber'      # ice amber night pink warm cool none
    su -c 'ptcolor set #66CCFF'
    su -c 'ptcolor strength 0.7'      # 0-1
    su -c 'ptcolor brightness 1.3'    # 0-4
    su -c 'ptcolor off'               # stock passthrough after a restart

mrservice reads its shaders when it starts, so each change shows after a restart.

## Status

Not yet installed on our headset, 2026-11-19.
