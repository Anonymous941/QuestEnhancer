#!/system/bin/sh
# Runs before Magisk mounts modules, so the overlay is ready before zygote.
# Only writes when the apk is unpatched or the colour changed, so a firmware
# update gets re-patched on the next boot.

MODDIR=${0%/*}
. $MODDIR/ptlib.sh

apply_tint
[ $? -eq 0 ] && clear_shader_cache
exit 0
