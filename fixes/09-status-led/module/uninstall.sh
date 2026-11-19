#!/system/bin/sh
# Stop the daemon and hand the LED back.

pkill -f "questled.sh" 2>/dev/null

for ch in red green blue; do
    [ -w "/sys/class/leds/$ch/blink" ] && echo 0 > "/sys/class/leds/$ch/blink"
    [ -w "/sys/class/leds/$ch/brightness" ] && echo 0 > "/sys/class/leds/$ch/brightness"
done

# questled.conf stays, so settings survive a reinstall.
rm -f /data/adb/questled.log

exit 0
