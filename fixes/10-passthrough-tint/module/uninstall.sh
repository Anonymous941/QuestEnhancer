#!/system/bin/sh
# Dropping the module dir is already a full revert. persist.* survives a reboot
# on its own, so clear it explicitly.

MODDIR=${0%/*}
. $MODDIR/ptlib.sh

clear_shader_cache
resetprop --delete persist.oculus.mrservice.eyebuffer_force_rgba 2>/dev/null
rm -rf /data/adb/$MODID
exit 0
