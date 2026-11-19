#!/system/bin/sh
# Put a logo in place for the next boot animation.
#   sh pick.sh          a random logo (not the same as now)
#   sh pick.sh NAME     logos/NAME.png
#   sh pick.sh list     list the logos
MODDIR=${0%/*}
LOGO="$MODDIR/system/etc/meta_logo.png"
cd "$MODDIR/logos" || exit 1
if [ "$1" = list ]; then ls *.png | sed 's/\.png$//'; exit 0; fi
if [ -n "$1" ]; then
  PICK="$1.png"
  [ -f "$PICK" ] || { echo "no logo named $1 (sh pick.sh list)"; exit 1; }
else
  set -- *.png
  [ $# -gt 0 ] || exit 1
  # skip the logo already in place, if there is another
  i=0
  while :; do
    n=$(( $(od -An -N2 -tu2 /dev/urandom) % $# + 1 ))
    eval PICK=\${$n}
    i=$((i + 1))
    # same as the logo in place: try again (up to 10 times)
    if cmp -s "$PICK" "$LOGO" && [ $# -gt 1 ] && [ $i -lt 10 ]; then continue; fi
    break
  done
fi
# Write into the file in place: /system/etc/meta_logo.png is a bind mount of it.
cat "$PICK" > "$LOGO"
echo "boot logo: ${PICK%.png}"
