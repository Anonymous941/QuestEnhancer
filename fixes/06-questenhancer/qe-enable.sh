#!/system/bin/sh
# Turn QuestEnhancer on in Vector for SystemUX, VrShell and Settings (user 0), then show the result.
# Takes effect at the next reboot (Vector loads modules when an app process starts).
CLI="sh /data/adb/modules/zygisk_vector/cli"
M=org.codeberg.anonymous950.questenhancer
echo "== enable"; $CLI modules enable $M
echo "== scope set"; $CLI scope set $M com.oculus.systemux/0 com.oculus.vrshell/0 com.oculus.panelapp.settings/0
echo "== modules ls"; $CLI modules ls
echo "== scope ls"; $CLI scope ls $M
echo "== status"; $CLI status
