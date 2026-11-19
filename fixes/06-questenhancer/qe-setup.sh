#!/system/bin/sh
# QuestEnhancer setup, run as root: sh qe-setup.sh
# 1. Turns on Zygisk (Vector needs it). 2. Installs the Vector (or LSPosed) zip from /sdcard/Download.
# 3. Installs QuestEnhancer.apk from /sdcard/Download. Reboot afterwards, then run qe-enable.sh.
echo "Magisk: $(magisk -v) ($(magisk -V))"
Z=$(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'" | sed 's/.*=//')
echo "zygisk setting before: ${Z:-unset}"
magisk --sqlite "REPLACE INTO settings (key,value) VALUES('zygisk',1)"
echo "zygisk setting now: $(magisk --sqlite "SELECT value FROM settings WHERE key='zygisk'")"
L=$(ls /sdcard/Download/Vector-*.zip /sdcard/Download/LSPosed*.zip 2>/dev/null | head -1)
if [ -n "$L" ]; then echo "installing $L"; magisk --install-module "$L"; else echo "no Vector or LSPosed zip in /sdcard/Download"; fi
if [ -f /sdcard/Download/QuestEnhancer.apk ]; then
  cp /sdcard/Download/QuestEnhancer.apk /data/local/tmp/qe.apk && pm install -r /data/local/tmp/qe.apk
else echo "no /sdcard/Download/QuestEnhancer.apk"; fi
echo "modules:"; ls /data/adb/modules
