#!/system/bin/sh
# Run as root on the headset: sh ocms-scope.sh add|remove
# Sets QuestEnhancer's Vector scope (user 0) with or without com.oculus.ocms,
# keeping SystemUX, VrShell, Settings, Horizon and vrcast.
CLI="sh /data/adb/modules/zygisk_vector/cli"
M=org.codeberg.anonymous950.questenhancer
BASE="com.oculus.systemux/0 com.oculus.vrshell/0 com.oculus.panelapp.settings/0 com.oculus.horizon/0 com.oculus.vrcast/0"
echo "== scope before"; $CLI scope ls $M
case "$1" in
  add)    $CLI scope set $M $BASE com.oculus.ocms/0 ;;
  remove) $CLI scope set $M $BASE ;;
  *) echo "usage: ocms-scope.sh add|remove"; exit 1 ;;
esac
echo "== scope after"; $CLI scope ls $M
