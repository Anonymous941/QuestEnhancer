#!/system/bin/sh

MODDIR=${0%/*}

# late_start is late, but the battery nodes are not guaranteed up yet and the
# daemon only waits on the LED.
i=0
while [ "$(getprop sys.boot_completed)" != "1" ]; do
    i=$(( i + 1 ))
    [ "$i" -gt 120 ] && break
    sleep 2
done

# Never stack instances.
pgrep -f "questled.sh" >/dev/null 2>&1 && exit 0

nohup sh "$MODDIR/questled.sh" >/dev/null 2>&1 &
exit 0
