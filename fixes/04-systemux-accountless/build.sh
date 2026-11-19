#!/bin/sh
# Build the systemux-accountless Magisk module from YOUR headset's SystemUX.apk.
# Upstream: https://github.com/LucentW/systemux-accountless (pinned commit below).
# Needs: git, python3, java, and smali/baksmali 2.5.2 on PATH
#   (https://bitbucket.org/JesusFreke/smali/downloads/).
#   sh build.sh /path/to/SystemUX.apk   ->  build/systemux_dock-v7.zip
set -eu
UPSTREAM=https://github.com/LucentW/systemux-accountless.git
COMMIT=7e2a519
APK=$(realpath "$1")
HERE=$(cd "$(dirname "$0")" && pwd)
SRC="$HERE/build/upstream"
rm -rf "$SRC" && git clone -q "$UPSTREAM" "$SRC" && git -C "$SRC" checkout -q "$COMMIT"
# The APK we built against (Quest 1 v50, build 49845030443200410):
echo "be425390033abd895e8cbded124c658033714cf6ea3512edd71d97d30036c9ed  $APK" | sha256sum -c - \
  || echo "WARNING: different SystemUX.apk; the build still stops if any patch point is missing"
python3 -I "$SRC/build.py" "$APK" "$HERE/build"
