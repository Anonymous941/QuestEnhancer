#!/system/bin/sh

SKIPUNZIP=0
MODDIR=$MODPATH
. $MODPATH/ptlib.sh

ui_print " "
ui_print "  Quest 1 Passthrough Colorizer"
ui_print " "

# Colour survives an upgrade.
OLDCONF=/data/adb/modules/$MODID/tint.conf
if [ -f "$OLDCONF" ]; then
  cp -f "$OLDCONF" "$MODPATH/tint.conf"
  ui_print "  - kept your existing tint.conf"
fi

set_perm_recursive "$MODPATH" 0 0 0755 0644
[ -f "$MODPATH/system/bin/ptcolor" ] && set_perm "$MODPATH/system/bin/ptcolor" 0 0 0755

load_config
ui_print "  colour   $TINT_COLOR"
ui_print "  strength $STRENGTH   brightness $BRIGHTNESS"
ui_print " "

apply_tint
rc=$?
if [ "$ENABLED" != 1 ]; then rc=9; fi
case $rc in
  9) ui_print "  = ENABLED=0 in tint.conf, stock passthrough left in place"
     ui_print "    turn it on with: su -c 'ptcolor on'" ;;
  0) clear_shader_cache
     ui_print "  + passthrough tint applied"
     ui_print "    the tint lives in mrservice's own passthrough sampler,"
     ui_print "    so UI and 3D content are untouched"
     ui_print "    reboot to see it" ;;
  1) ui_print "  = already up to date" ;;
  *) ui_print "  ! patch failed, module installed but inactive"
     ui_print "    see /data/adb/$MODID/ptcolor.log" ;;
esac
ui_print " "
ui_print "  Change the colour any time with:"
ui_print "    su -c 'ptcolor set #FFB347'"
ui_print " "
