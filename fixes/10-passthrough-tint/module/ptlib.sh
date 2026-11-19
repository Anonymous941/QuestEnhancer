#!/system/bin/sh
# Shared logic. Sourced by customize.sh, post-fs-data.sh, uninstall.sh, ptcolor.
#
# mrservice builds its shaders from obfuscated blobs in libmrservice.so. The
# one that matters holds samplePassthroughTextures(), which every passthrough
# style calls and nothing else does. Rewriting its greyscale return tints
# passthrough and nothing else.
#
# The blob is XOR'd against a position-dependent keystream, its own inverse.
# mr_patch holds two same-length rewrites, so nothing shifts.

MODID=questptcolor
MODDIR=${MODDIR:-/data/adb/modules/$MODID}
CONF="$MODDIR/tint.conf"
STATE=/data/adb/$MODID
LOG="$STATE/ptcolor.log"

STOCK_APK=/system/priv-app/MrServiceMonterey/MrServiceMonterey.apk
OVERLAY_DIR="$MODDIR/system/priv-app/MrServiceMonterey"
OVERLAY="$OVERLAY_DIR/MrServiceMonterey.apk"

# Offsets are build-specific. Every window is checked before writing, so a
# firmware update is a no-op rather than corruption.
TABLE_OFF=2354633
COLOUR_OFF=5831
COLOUR_LEN=17

log() {
  mkdir -p "$STATE"
  echo "$(date '+%m-%d %H:%M:%S') $*" >>"$LOG"
  [ "$(wc -l <"$LOG" 2>/dev/null || echo 0)" -gt 400 ] && \
    { tail -n 150 "$LOG" >"$LOG.tmp"; mv "$LOG.tmp" "$LOG"; }
  return 0
}

say() { command -v ui_print >/dev/null 2>&1 && ui_print "$1" || echo "$1"; }

load_config() {
  ENABLED=1
  TINT_COLOR=#66CCFF
  STRENGTH=1.0
  BRIGHTNESS=1.0
  [ -f "$CONF" ] && . "$CONF"
  return 0
}

read_hex() { dd if="$1" bs=1 skip="$2" count="$3" 2>/dev/null | od -An -v -tx1 | tr -d ' \n'; }

# LC_ALL=C keeps printf %c to single bytes.
hex_bytes() {
  echo "$1" | LC_ALL=C awk '{
    for(i=1;i<=length($0);i+=2){
      h=tolower(substr($0,i,2)); n=0
      for(k=1;k<=2;k++) n = n*16 + index("0123456789abcdef", substr(h,k,1)) - 1
      printf "%c", n
    } }'
}

# Produces the exact 17 characters the shader expects.
colour_text() {
  echo "$1" | awk -v s="$2" -v br="$3" '
    function hv(c){ return index("0123456789abcdef", tolower(c)) - 1 }
    function cl(v,l,h){ return v<l?l:(v>h?h:v) }
    {
      t=$0; gsub(/[^0-9A-Fa-f]/,"",t)
      if (length(t)==3)
        t=substr(t,1,1) substr(t,1,1) substr(t,2,1) substr(t,2,1) substr(t,3,1) substr(t,3,1)
      if (length(t)!=6) { printf "1.000,1.000,1.000"; exit }
      r=(hv(substr(t,1,1))*16+hv(substr(t,2,1)))/255
      g=(hv(substr(t,3,1))*16+hv(substr(t,4,1)))/255
      b=(hv(substr(t,5,1))*16+hv(substr(t,6,1)))/255
      s=cl(s+0,0,1); br=cl(br+0,0,4)
      printf "%.3f,%.3f,%.3f", cl(((1-s)+s*r)*br,0,9.999), cl(((1-s)+s*g)*br,0,9.999), cl(((1-s)+s*b)*br,0,9.999)
    }'
}

_ks_dec() { od -An -v -tu1 "$MODDIR/mr_colour_ks.bin" | tr '\n' ' '; }

# awk has no bitwise ops, hence the bit loop.
write_colour_bytes() {
  _vals=$(echo "$1" | awk '
    function ord(c,  p){ p=index("0123456789.,",c)
      if(p>=1 && p<=10) return 47+p
      if(p==11) return 46
      return 44 }
    { for(i=1;i<=length($0);i++) printf "%d ", ord(substr($0,i,1)) }')
  LC_ALL=C awk -v ks="$(_ks_dec)" -v vals="$_vals" '
    function bxor(a,b,  r,i,x,y){ r=0
      for(i=0;i<8;i++){ x=int(a/(2^i))%2; y=int(b/(2^i))%2; if(x!=y) r+=2^i }
      return r }
    BEGIN{ split(ks,K," "); n=split(vals,V," ")
      for(i=1;i<=n;i++) printf "%c", bxor(V[i]+0, K[i]+0) }'
}

read_colour() {
  _raw=$(dd if="$1" bs=1 skip=$(( TABLE_OFF + COLOUR_OFF )) count="$COLOUR_LEN" 2>/dev/null \
         | od -An -v -tu1 | tr '\n' ' ')
  awk -v ks="$(_ks_dec)" -v raw="$_raw" '
    function bxor(a,b,  r,i,x,y){ r=0
      for(i=0;i<8;i++){ x=int(a/(2^i))%2; y=int(b/(2^i))%2; if(x!=y) r+=2^i }
      return r }
    BEGIN{ split(ks,K," "); n=split(raw,R," "); out=""
      for(i=1;i<=n;i++) out = out sprintf("%c", bxor(R[i]+0, K[i]+0))
      print out }'
}

colour_is_sane() {
  echo "$1" | grep -qE '^[0-9]\.[0-9]{3},[0-9]\.[0-9]{3},[0-9]\.[0-9]{3}$'
}

# Refuse outright on another build rather than trusting the byte checks alone.
BUILD_DEVICE=monterey
BUILD_INCREMENTAL=49845030443200410

check_build() {
  _dev=$(getprop ro.product.device)
  _inc=$(getprop ro.build.version.incremental)
  if [ "$_dev" != "$BUILD_DEVICE" ] || [ "$_inc" != "$BUILD_INCREMENTAL" ]; then
    say "  ! built for $BUILD_DEVICE / $BUILD_INCREMENTAL"
    say "    this device is $_dev / $_inc"
    say "    refusing to patch an unverified build"
    log "build mismatch: $_dev/$_inc"
    return 1
  fi
  return 0
}

# Patch a private copy. /system is never touched, so removal is a full revert.
ensure_overlay() {
  [ -f "$OVERLAY" ] && return 0
  [ -f "$STOCK_APK" ] || { say "  ! MrServiceMonterey.apk not found"; return 2; }
  mkdir -p "$OVERLAY_DIR"
  cat "$STOCK_APK" >"$OVERLAY" || return 2
  chmod 644 "$OVERLAY"
  log "created overlay from $STOCK_APK"
  return 0
}

disable_overlay() {
  if [ -f "$OVERLAY" ]; then
    rm -f "$OVERLAY"
    rmdir "$OVERLAY_DIR" 2>/dev/null
    rmdir "$MODDIR/system/priv-app" 2>/dev/null
    log "overlay removed, stock passthrough restored"
    return 0
  fi
  return 1
}

# Expected bytes are stored as a hash, not verbatim, so no original shader
# ships here. A window is only written once its current bytes match.
apply_patch() {
  while read -r _off _exp _new; do
    [ -n "$_off" ] || continue
    _n=$(( ${#_new} / 2 ))
    _at=$(( TABLE_OFF + _off ))
    _cur=$(read_hex "$OVERLAY" "$_at" "$_n")
    [ "$_cur" = "$_new" ] && continue
    _curmd5=$(dd if="$OVERLAY" bs=1 skip="$_at" count="$_n" 2>/dev/null | md5sum | cut -d' ' -f1)
    if [ "$_curmd5" != "$_exp" ]; then
      # The colour window differs once a colour has been written.
      if [ "$_off" -le "$COLOUR_OFF" ] && \
         [ $(( _off + _n )) -gt "$COLOUR_OFF" ] && \
         colour_is_sane "$(read_colour "$OVERLAY")"; then
        :
      else
        say "  ! shader bytes at $_at are not the expected build"
        log "window $_off mismatch: md5 $_curmd5, expected $_exp"
        return 2
      fi
    fi
    hex_bytes "$_new" | dd of="$OVERLAY" bs=1 seek="$_at" conv=notrunc 2>/dev/null
  done <"$MODDIR/mr_patch"
  return 0
}

# 0 = changed, 1 = already current, 2 = failed.
apply_tint() {
  load_config
  if [ "$ENABLED" != 1 ]; then
    disable_overlay && return 0
    return 1
  fi
  check_build || return 2
  ensure_overlay || return 2

  _want=$(colour_text "$TINT_COLOR" "$STRENGTH" "$BRIGHTNESS")
  if [ "$(read_colour "$OVERLAY")" = "$_want" ]; then
    log "already current ($_want)"
    return 1
  fi

  apply_patch || return 2
  write_colour_bytes "$_want" \
    | dd of="$OVERLAY" bs=1 seek=$(( TABLE_OFF + COLOUR_OFF )) conv=notrunc 2>/dev/null

  _got=$(read_colour "$OVERLAY")
  if [ "$_got" != "$_want" ]; then
    say "  ! write-back verify failed (got '$_got' want '$_want')"
    log "verify failed: got $_got want $_want"
    return 2
  fi
  log "patched $OVERLAY colour=$_want ($TINT_COLOR)"
  return 0
}

# A stale shader cache would keep the old colour.
clear_shader_cache() {
  rm -f /data/user_de/0/com.oculus.mrservice/code_cache/com.android.opengl.shaders_cache/* 2>/dev/null
  rm -rf /data/data/com.oculus.mrservice/cache/* 2>/dev/null
  return 0
}
