#!/system/bin/sh
# Holds the backlight at VALUE while the display is on (PIN=1). Never writes
# while the backlight is 0 (display off), so it can't light a sleeping panel.
# Re-reads the conf on SIGUSR1 (qebacklight sends it) or when the file is newer.

BL=/sys/class/leds/lcd-backlight/brightness
MAX=/sys/class/leds/lcd-backlight/max_brightness
CONF=/data/adb/qebacklight.conf
STAMP=/data/adb/.qebacklight.loaded

load() {
    PIN=0; VALUE=255
    [ -r "$CONF" ] && . "$CONF"
    : > "$STAMP"
    case $VALUE in ''|*[!0-9]*) VALUE=255 ;; esac
    read -r _m < "$MAX" 2>/dev/null
    case $_m in ''|*[!0-9]*) _m=255 ;; esac
    [ "$VALUE" -gt "$_m" ] && VALUE=$_m
    [ "$VALUE" -lt 1 ] && VALUE=1
}

RELOAD=0
trap 'RELOAD=1' USR1
load
while :; do
    if [ "$RELOAD" = 1 ] || [ "$CONF" -nt "$STAMP" ]; then RELOAD=0; load; fi
    if [ "$PIN" = 1 ]; then
        read -r _b < "$BL" 2>/dev/null
        case $_b in ''|*[!0-9]*) _b=0 ;; esac
        [ "$_b" -gt 0 ] && [ "$_b" != "$VALUE" ] && echo "$VALUE" > "$BL"
    fi
    sleep 2
done
