#!/system/bin/sh
# After each boot, choose a random logo for the next boot animation.
MODDIR=${0%/*}
sh "$MODDIR/pick.sh" > "$MODDIR/last-pick.txt" 2>&1
