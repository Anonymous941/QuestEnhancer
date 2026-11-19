# 06: QuestEnhancer (Xposed module, via Vector)

[Bubbagum88/QuestEnhancer](https://github.com/Bubbagum88/QuestEnhancer) (fork of
Anonymous941's, GPL-3.0) hooks SystemUX, VrShell and Settings: feature flags, Pin/Unpin for
Unknown Sources apps, and (our additions, `DockLibraryHooks.kt`) up to 10 pinned apps, no
default Messenger/Share pins, and a local-apps Library when there is no Meta account.
Build: GitHub Actions on the fork (branch `claude/project-thread-a9maiw`), artifact APK.

It needs an Xposed framework. LSPosed's maintained fork is now **Vector** (JingMatrix,
zygisk module id `zygisk_vector`; tested v2.2-3117), which needs Zygisk on in Magisk.

## Install (from platform-tools, with QuestEnhancer.apk and Vector-*.zip next to adb)

    .\adb push QuestEnhancer.apk /sdcard/Download/
    .\adb push Vector-v2.2-3117-Release.zip /sdcard/Download/
    .\adb push qe-setup.sh qe-enable.sh qe-check.sh /data/local/tmp/
    .\adb shell "su -c 'sh /data/local/tmp/qe-setup.sh'"    # Zygisk on, Vector + APK installed
    .\adb reboot
    .\adb shell "su -c 'sh /data/local/tmp/qe-enable.sh'"   # enable + scope, via Vector's CLI
    .\adb reboot                                            # modules load at app start
    .\adb shell "su -c 'sh /data/local/tmp/qe-check.sh'"    # log to /sdcard/Download/qe-check.txt

The scope is set with Vector's CLI (`/data/adb/modules/zygisk_vector/cli`: `modules enable`,
`scope set PKG app/user ...`), so no manager app is needed in VR.

## Undo

    .\adb shell "su -c 'sh /data/adb/modules/zygisk_vector/cli modules disable org.codeberg.anonymous950.questenhancer'"
    .\adb uninstall org.codeberg.anonymous950.questenhancer
    .\adb shell "su -c 'touch /data/adb/modules/zygisk_vector/remove'"   # removes Vector at reboot

## Status

2026-11-06 (v50 build 49845030443200410, Magisk 30.7): installed; after the second reboot
Pin confirmed working on the test headset. Gotcha: enabling a module does nothing until the
apps restart; the first try showed only "Uninstall" because the headset had booted before
`qe-enable.sh`.
