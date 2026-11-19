#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Quest 1 display brightness pin"
if [ ! -f /data/adb/qebacklight.conf ]; then
    cat > /data/adb/qebacklight.conf <<'CONF'
# 1 holds the backlight at VALUE while the display is on; 0 leaves it to the system.
PIN=0
# 1-255 (the max is in /sys/class/leds/lcd-backlight/max_brightness).
VALUE=255
CONF
    chmod 644 /data/adb/qebacklight.conf
fi
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/backlightd.sh" 0 0 0755
set_perm "$MODPATH/system/bin/qebacklight" 0 0 0755
ui_print "- Off until you turn it on: su -c 'qebacklight pin on'"
