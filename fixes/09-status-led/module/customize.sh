#!/system/bin/sh

SKIPUNZIP=0

ui_print "- Quest LED"

if [ ! -e /sys/class/leds/red/brightness ]; then
    ui_print "! /sys/class/leds/red/brightness not found."
    ui_print "! This module targets Quest headsets with the PMI8998 RGB LED."
    ui_print "! Installing anyway; the daemon will exit if the node never appears."
fi

# Settings survive module updates.
if [ ! -f /data/adb/questled.conf ]; then
    ui_print "- Writing default config to /data/adb/questled.conf"
    cat > /data/adb/questled.conf <<'CONF'
# Quest LED settings. Changes apply at once with the questled command or the
# QuestEnhancer Manager; a hand edit here applies within a second or two.
# Effects: rainbow battery solid breathe police off

# 0 hands the LED back dark (the daemon waits; 1 turns it on again).
ENABLED=1

# While you wear the headset.
AWAKE_EFFECT=rainbow
COLOR=#00A0FF

# Charging (asleep, or worn too if CHARGE_WHEN_WORN=1).
CHARGE_EFFECT=battery
CHARGE_COLOR=#FF6000

# Charged and still plugged in.
FULL_EFFECT=solid
FULL_COLOR=#00FF40

# Asleep on battery; dark after SLEEP_TIMEOUT seconds (0 = never).
SLEEP_EFFECT=battery
SLEEP_COLOR=#00A0FF
SLEEP_TIMEOUT=90

# Low battery warning (1 = on): asleep on battery at or below LOW_BATT %.
# Shown until plugged in or worn, with no timeout.
LOW_ENABLED=0
LOW_BATT=15
LOW_EFFECT=breathe
LOW_COLOR=#FF0000

# Boot finished animation (1 = on), played for BOOT_SECONDS.
BOOT_ENABLED=0
BOOT_EFFECT=rainbow
BOOT_COLOR=#00A0FF
BOOT_SECONDS=4

# Show the charging/full effect while worn too.
CHARGE_WHEN_WORN=0

# Seconds for one rainbow turn or one breath.
SPEED=8

# 60 costs about 4% of one core while animating. 30 halves it.
FPS=60

# Brightness 0-255: worn, and asleep (lower, so it isn't glaring at night).
BRIGHT=255
SLEEP_BRIGHT=90

# Gamma correction, so ramps look even.
GAMMA=1

# 1 logs more to /data/adb/questled.log.
DEBUG=0
CONF
    chmod 644 /data/adb/questled.conf
else
    ui_print "- Keeping existing /data/adb/questled.conf"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/questled.sh" 0 0 0755
set_perm "$MODPATH/service.sh"  0 0 0755
set_perm "$MODPATH/system/bin/questled" 0 0 0755

ui_print "- Done. Reboot to start, then: su -c 'questled'"
