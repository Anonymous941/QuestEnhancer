#!/system/bin/sh
SKIPUNZIP=0
ui_print "- Quest 1 UI sounds"

# Sounds you set survive a module update.
OLD=/data/adb/modules/quest1_ui_sounds/system/product/media/audio/ui
if [ -d "$OLD" ]; then
    mkdir -p "$MODPATH/system/product/media/audio/ui"
    cp -f "$OLD"/*.ogg "$MODPATH/system/product/media/audio/ui/" 2>/dev/null \
        && ui_print "- Kept your custom sounds"
fi

mkdir -p /sdcard/QuestEnhancer/sounds
set_perm_recursive "$MODPATH" 0 0 0755 0644
set_perm "$MODPATH/system/bin/qesound" 0 0 0755
ui_print "- Put .ogg files in /sdcard/QuestEnhancer/sounds, then:"
ui_print "    su -c 'qesound set ChargingStarted /sdcard/QuestEnhancer/sounds/charging.ogg'"
ui_print "- A new sound plays after a restart."
