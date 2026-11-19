#!/system/bin/sh
# Builds a boot image that shows a random logo from the first frame of the boot animation.
# Run on the headset as root. Reads /sdcard/Download/boot_a.img (your current, Magisk-patched
# boot_a), writes /sdcard/Download/boot_a_logo.img. Flashes nothing.
#
# All logos go into the ramdisk (Magisk overlay.d/sbin -> /sbin). At post-fs, before late-fs
# starts bootanim, init runs q1pick.sh in Magisk's SELinux domain (init itself may not mount
# over a system_file), which bind-mounts a random logo over /system/etc/meta_logo.png.
# Logos come from LOGOS (default: the quest1_boot_logo module's logos/ folder).
set -e
MB=/data/adb/magisk/magiskboot
LOGOS=${LOGOS:-/data/adb/modules/quest1_boot_logo/logos}
W=/data/local/tmp/q1logo
rm -rf "$W"; mkdir -p "$W"; cd "$W"
cp /sdcard/Download/boot_a.img boot.img

cat > q1pick.sh <<'SH'
#!/system/bin/sh
# Early boot: put a random logo over /system/etc/meta_logo.png.
set -- /sbin/q1logo_*.png
[ -f "$1" ] || { echo "q1logo: no logos in /sbin" > /dev/kmsg; exit 0; }
n=$(( $(od -An -N2 -tu2 /dev/urandom) % $# + 1 ))
eval L=\${$n}
if mount -o bind "$L" /system/etc/meta_logo.png; then
  echo "q1logo: $L" > /dev/kmsg
else
  echo "q1logo: mount of $L failed" > /dev/kmsg
fi
SH

cat > q1logo.rc <<'RC'
on post-fs
    exec u:r:magisk:s0 root root -- /system/bin/sh ${MAGISKTMP}/q1pick.sh
RC

$MB unpack boot.img
set -- "mkdir 0750 overlay.d" "mkdir 0750 overlay.d/sbin" \
       "add 0644 overlay.d/q1logo.rc q1logo.rc" \
       "add 0755 overlay.d/sbin/q1pick.sh q1pick.sh"
for f in "$LOGOS"/*.png; do
  n=$(basename "$f")
  set -- "$@" "add 0644 overlay.d/sbin/q1logo_$n $f"
done
$MB cpio ramdisk.cpio "$@"
echo "--- overlay.d in the new ramdisk"
$MB cpio ramdisk.cpio "ls" 2>/dev/null | grep -E "overlay.d|q1" || true
$MB repack boot.img new-boot.img
cp new-boot.img /sdcard/Download/boot_a_logo.img
sha256sum /sdcard/Download/boot_a.img /sdcard/Download/boot_a_logo.img
