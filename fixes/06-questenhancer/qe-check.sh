#!/system/bin/sh
# After a reboot: is QuestEnhancer loaded, and did each hook install? Writes /sdcard/Download/qe-check.txt
O=/sdcard/Download/qe-check.txt
{
echo "== uptime"; uptime
echo "== boot logo"; dmesg | grep q1logo
echo "== zygisk / vector"; magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'"; ls /data/adb/modules
sh /data/adb/modules/zygisk_vector/cli status
sh /data/adb/modules/zygisk_vector/cli modules ls
echo "== processes"; for p in com.oculus.systemux com.oculus.vrshell com.oculus.panelapp.settings; do echo "$p $(pidof $p)"; done
echo "== vector log files"; find /data/adb/vector /data/adb/lspd /data/adb/modules/zygisk_vector -maxdepth 3 -name "*.log" 2>/dev/null | head
for f in $(find /data/adb/vector /data/adb/lspd -maxdepth 3 -name "*.log" 2>/dev/null | head -5); do echo "--- $f"; grep -iE "questenhancer|DockLibrary|QuestEnhancer/" "$f" | tail -80; done
echo "== logcat (QuestEnhancer, Vector, Xposed, crashes)"
logcat -d -b main,system,crash | grep -iE "QuestEnhancer|DockLibrary|Vector|Xposed|LSPosed|FATAL|AndroidRuntime" | tail -200
} > $O 2>&1
echo "wrote $O ($(wc -l < $O) lines)"
