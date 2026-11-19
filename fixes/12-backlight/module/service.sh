#!/system/bin/sh
MODDIR=${0%/*}
while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done
pgrep -f backlightd.sh >/dev/null 2>&1 && exit 0
nohup sh "$MODDIR/backlightd.sh" >/dev/null 2>&1 &
exit 0
