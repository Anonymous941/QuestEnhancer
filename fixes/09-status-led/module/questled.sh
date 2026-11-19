#!/system/bin/sh
#
# Status LED daemon: an effect for each state of the headset.
#
#   worn                      AWAKE_EFFECT  COLOR         BRIGHT
#   charging (asleep, or worn with CHARGE_WHEN_WORN=1)
#                             CHARGE_EFFECT CHARGE_COLOR  (SLEEP_)BRIGHT
#   charged, still plugged in FULL_EFFECT   FULL_COLOR
#   asleep on battery         SLEEP_EFFECT  SLEEP_COLOR   SLEEP_BRIGHT
#                             (dark after SLEEP_TIMEOUT seconds; 0 = never)
#   low battery (asleep on battery at or below LOW_BATT %, 0 = off)
#                             LOW_EFFECT    LOW_COLOR     SLEEP_BRIGHT
#                             (shown until plugged in or worn; no timeout)
#
# Events, played over the state and then back to it:
#   boot finished             BOOT_EFFECT   BOOT_COLOR    for BOOT_SECONDS (0 = off)
#
# Effects: rainbow battery solid breathe police off.
#
# Based on Juspertinry's q1_led v1.0.1 (MIT, github.com/Juspertinry/q1-modules):
# the rainbow, the battery ramp, fifo frame timing, awake detection and the
# always-write-all-channels rule are theirs. QuestEnhancer added the states,
# solid/breathe/police, colours, and settings that apply live: the daemon
# re-reads questled.conf on SIGUSR1 (the questled command sends it) or when
# the file is newer; ENABLED=0 darkens the LED and waits instead of exiting.
#
# Frame timing uses `read -t` on a fifo, not `sleep`: toybox sleep forks
# (~35 ms). The frame loop avoids `$(...)` for the same reason.

LEDS=/sys/class/leds
R=$LEDS/red
G=$LEDS/green
B=$LEDS/blue
PANEL=/sys/class/graphics/fb0/show_blank_event
BL=$LEDS/lcd-backlight/brightness
CAP=/sys/class/power_supply/battery/capacity
USB_ONLINE=/sys/class/power_supply/usb/online
DC_ONLINE=/sys/class/power_supply/dc/online
BATT_STATUS=/sys/class/power_supply/battery/status
CONF=/data/adb/questled.conf
LOG=/data/adb/questled.log
STAMP=/data/adb/.questled.loaded   # touched at each load; conf newer = reload

TICKFD=9
HAVE_TICK=0
LED_R=-1; LED_G=-1; LED_B=-1
r=0; g=0; b=0
CUR_BRIGHT=255
BATT=50

log() { echo "$(date '+%Y-%m-%d %H:%M:%S') $*" >> "$LOG"; }

# hex "#RRGGBB" -> r g b in HR HG HB (mksh reads 16#xx itself, no fork).
hex_rgb() {
    _c=${1#\#}
    case $_c in
        [0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f][0-9A-Fa-f]) ;;
        *) _c=$2 ;;
    esac
    HR=$(( 16#${_c%????} ))
    _t=${_c#??}; HG=$(( 16#${_t%??} ))
    HB=$(( 16#${_c#????} ))
}

num_or() { case $1 in ''|*[!0-9]*) echo "$2" ;; *) echo "$1" ;; esac; }

# Reads the config again from defaults, so a removed line falls back.
load_conf() {
    ENABLED=1
    AWAKE_EFFECT=rainbow;  COLOR=#00A0FF
    CHARGE_EFFECT=battery; CHARGE_COLOR=#FF6000
    FULL_EFFECT=solid;     FULL_COLOR=#00FF40
    SLEEP_EFFECT=battery;  SLEEP_COLOR=#00A0FF
    LOW_BATT=15;           LOW_EFFECT=breathe;    LOW_COLOR=#FF0000
    BOOT_EFFECT=rainbow;   BOOT_COLOR=#00A0FF;    BOOT_SECONDS=4
    CHARGE_WHEN_WORN=0
    SPEED=8; FPS=60; BRIGHT=255; SLEEP_BRIGHT=90; SLEEP_TIMEOUT=90
    GAMMA=1; DEBUG=0
    [ -r "$CONF" ] && . "$CONF"
    : > "$STAMP"

    # A typo in the config must not wedge the arithmetic below. (Forks, but
    # only when the settings change.)
    SPEED=$(num_or "$SPEED" 8);               [ "$SPEED" -lt 1 ] && SPEED=1
    FPS=$(num_or "$FPS" 60);                  [ "$FPS" -lt 1 ] && FPS=1; [ "$FPS" -gt 100 ] && FPS=100
    BRIGHT=$(num_or "$BRIGHT" 255);           [ "$BRIGHT" -gt 255 ] && BRIGHT=255
    SLEEP_BRIGHT=$(num_or "$SLEEP_BRIGHT" 90); [ "$SLEEP_BRIGHT" -gt 255 ] && SLEEP_BRIGHT=255
    SLEEP_TIMEOUT=$(num_or "$SLEEP_TIMEOUT" 90)
    LOW_BATT=$(num_or "$LOW_BATT" 15);        [ "$LOW_BATT" -gt 100 ] && LOW_BATT=100
    BOOT_SECONDS=$(num_or "$BOOT_SECONDS" 4); [ "$BOOT_SECONDS" -gt 60 ] && BOOT_SECONDS=60

    hex_rgb "$COLOR" 00A0FF;        AR=$HR;  AG=$HG;  AB=$HB
    hex_rgb "$CHARGE_COLOR" FF6000; CHR=$HR; CHG=$HG; CHB=$HB
    hex_rgb "$FULL_COLOR" 00FF40;   FR=$HR;  FG=$HG;  FB=$HB
    hex_rgb "$SLEEP_COLOR" 00A0FF;  SR=$HR;  SG=$HG;  SB=$HB
    hex_rgb "$LOW_COLOR" FF0000;    LR=$HR;  LG=$HG;  LB=$HB
    hex_rgb "$BOOT_COLOR" 00A0FF;   OR=$HR;  OG=$HG;  OB=$HB

    # Frame delay string, built once per load. Per frame would fork printf.
    ms=$(( 1000 / FPS ))
    [ "$ms" -lt 1 ] && ms=1
    if   [ "$ms" -ge 100 ]; then FRAME_DELAY="0.$ms"
    elif [ "$ms" -ge 10 ];  then FRAME_DELAY="0.0$ms"
    else                         FRAME_DELAY="0.00$ms"
    fi
    frames=$(( SPEED * FPS ))
    i=0
    mode=""          # pick the state again with the new values
    check=0
}

# Keep the log from growing without bound across reboots.
[ -f "$LOG" ] && [ "$(stat -c %s "$LOG" 2>/dev/null || echo 0)" -gt 65536 ] && : > "$LOG"

load_conf

# late_start is late enough in practice, but probe order is not guaranteed.
n=0
while [ ! -w "$R/brightness" ]; do
    n=$(( n + 1 ))
    [ "$n" -gt 60 ] && { log "giving up, $R/brightness never appeared"; exit 1; }
    sleep 1
done

# A fifo held open at both ends blocks on read, which makes `read -t` an
# accurate builtin delay. Unlink it straight away; the fd keeps it alive.

open_tick() {
    _f=/data/local/tmp/.questled.$$
    rm -f "$_f"
    mkfifo "$_f" 2>/dev/null || return 1
    eval "exec $TICKFD<> \"\$_f\"" 2>/dev/null || { rm -f "$_f"; return 1; }
    rm -f "$_f"
    HAVE_TICK=1
    return 0
}

tick() {
    if [ "$HAVE_TICK" = 1 ]; then
        read -t "$1" -u$TICKFD _junk 2>/dev/null
    else
        sleep "$1"
    fi
    return 0
}

# Always write all three channels. The stock light HAL repaints this LED on
# charge state changes, and a skip-if-unchanged cache loses to it permanently.
put() {
    echo "$1" > "$R/brightness"
    echo "$2" > "$G/brightness"
    echo "$3" > "$B/brightness"
    LED_R=$1; LED_G=$2; LED_B=$3   # recorded for DEBUG only, never for control
    return 0
}

# Gamma 2.0 as v*v/255. Close enough to 2.2 here, and stays integer.
emit() {
    if [ "$GAMMA" = 1 ]; then
        put $(( $1 * $1 * CUR_BRIGHT / 65025 )) \
            $(( $2 * $2 * CUR_BRIGHT / 65025 )) \
            $(( $3 * $3 * CUR_BRIGHT / 65025 ))
    else
        put $(( $1 * CUR_BRIGHT / 255 )) \
            $(( $2 * CUR_BRIGHT / 255 )) \
            $(( $3 * CUR_BRIGHT / 255 ))
    fi
}

clear_blink() {
    for _ch in red green blue; do
        [ -w "$LEDS/$_ch/blink" ] && echo 0 > "$LEDS/$_ch/blink"
    done
    return 0
}

# panel_power_on is the direct signal. Backlight is the fallback if that node
# is missing on another headset revision.

is_awake() {
    if [ -r "$PANEL" ]; then
        read -r _p < "$PANEL" 2>/dev/null
        case "$_p" in
            *"= 1") return 0 ;;
            *"= 0") return 1 ;;
        esac
    fi
    read -r _b < "$BL" 2>/dev/null || return 0
    [ "${_b:-1}" -gt 0 ]
}

# h is 0..1535.
h2rgb() {
    _h=$(( $1 % 1536 ))
    _f=$(( _h & 255 ))
    case $(( _h >> 8 )) in
        0) r=255;           g=$_f;           b=0             ;;
        1) r=$(( 255-_f )); g=255;           b=0             ;;
        2) r=0;             g=255;           b=$_f           ;;
        3) r=0;             g=$(( 255-_f )); b=255           ;;
        4) r=$_f;           g=0;             b=255           ;;
        *) r=255;           g=0;             b=$(( 255-_f )) ;;
    esac
}

# Two segments meeting at orange, so the midpoint reads amber rather than a
# muddy red-green blend.
batt_rgb() {
    _c=$1
    [ "$_c" -lt 0 ] 2>/dev/null && _c=0
    [ "$_c" -gt 100 ] 2>/dev/null && _c=100
    if [ "$_c" -le 50 ]; then
        r=255
        g=$(( _c * 128 / 50 ))
        b=0
    else
        r=$(( 255 - (_c - 50) * 255 / 50 ))
        g=$(( 128 + (_c - 50) * 127 / 50 ))
        b=0
    fi
}

read_batt() {
    read -r BATT < "$CAP" 2>/dev/null || BATT=50
    case "$BATT" in
        ''|*[!0-9]*) BATT=50 ;;
    esac
}

# usb/online stays 1 once the battery reads Full. status does not.
on_power() {
    read -r _o < "$USB_ONLINE" 2>/dev/null && [ "$_o" = 1 ] && return 0
    read -r _o < "$DC_ONLINE" 2>/dev/null && [ "$_o" = 1 ] && return 0
    read -r _s < "$BATT_STATUS" 2>/dev/null
    case "$_s" in
        Charging|Full) return 0 ;;
    esac
    return 1
}


# Charged: the battery says Full, or reads 100 while on power.
is_full() {
    read -r _s < "$BATT_STATUS" 2>/dev/null
    [ "$_s" = Full ] && return 0
    read_batt
    [ "$BATT" -ge 100 ]
}

cleanup() {
    trap - INT TERM EXIT
    clear_blink
    put 0 0 0
    [ "$HAVE_TICK" = 1 ] && eval "exec $TICKFD>&-"
    log "stopped"
    exit 0
}
trap cleanup INT TERM EXIT

# USR1, not HUP: service.sh starts us under nohup, and a shell can't trap a
# signal it started with ignored.
RELOAD=0
trap 'RELOAD=1' USR1

open_tick || log "no fifo available, falling back to sleep (frames will be choppy)"
clear_blink
log "started (enabled=$ENABLED worn=$AWAKE_EFFECT charging=$CHARGE_EFFECT full=$FULL_EFFECT asleep=$SLEEP_EFFECT)"

off_written=0
asleep_since=-1
battwait=0
ER=0; EG=0; EB=0
effect=off

# Boot finished: watched once a second until it happens (getprop forks, so not
# per frame). If the daemon starts long after boot (a module update restarts
# it), there is nothing to announce.
booted=0
boot_until=-1
next_boot_check=0
read -r _up _junk < /proc/uptime 2>/dev/null
[ "${_up%%.*}" -gt 300 ] 2>/dev/null && booted=1

while :; do
    # RELOAD is set by SIGUSR1 (exact); -nt may compare whole seconds only,
    # so it is the fallback for hand edits. -nt is a builtin: no fork.
    if [ "$RELOAD" = 1 ] || { [ "$check" -le 0 ] && [ "$CONF" -nt "$STAMP" ]; }; then
        RELOAD=0
        load_conf
        off_written=0
        log "settings reloaded (enabled=$ENABLED worn=$AWAKE_EFFECT/$COLOR charging=$CHARGE_EFFECT/$CHARGE_COLOR full=$FULL_EFFECT/$FULL_COLOR asleep=$SLEEP_EFFECT/$SLEEP_COLOR)"
    fi

    # ENABLED=0: hand the LED back dark and wait for the setting to change.
    if [ "$ENABLED" != 1 ]; then
        if [ "$off_written" = 0 ]; then clear_blink; put 0 0 0; off_written=1; fi
        tick 1
        check=0
        continue
    fi

    # Which state are we in? A file read every frame buys nothing: about six
    # times a second while animating, every pass otherwise.
    if [ "$check" -le 0 ]; then
        check=10
        if [ "$booted" = 0 ] && [ "$SECONDS" -ge "$next_boot_check" ]; then
            next_boot_check=$(( SECONDS + 1 ))
            if [ "$(getprop sys.boot_completed)" = 1 ]; then
                booted=1
                [ "$BOOT_SECONDS" -gt 0 ] && boot_until=$(( SECONDS + BOOT_SECONDS ))
            fi
        fi
        if is_awake; then awake=1; else awake=0; fi
        if on_power; then power=1; else power=0; fi
        if [ "$SECONDS" -lt "$boot_until" ]; then
            now=boot
        elif [ "$power" = 1 ] && { [ "$awake" = 0 ] || [ "$CHARGE_WHEN_WORN" = 1 ]; }; then
            if is_full; then now=full; else now=charging; fi
        elif [ "$awake" = 1 ]; then
            now=worn
        else
            now=asleep
            if [ "$LOW_BATT" -gt 0 ]; then
                read_batt
                [ "$BATT" -le "$LOW_BATT" ] && now=low
            fi
        fi
        if [ "$now" = asleep ]; then
            [ "$asleep_since" -lt 0 ] && asleep_since=$SECONDS
        else
            asleep_since=-1
        fi
        if [ "$now" != "$mode" ]; then
            mode=$now
            battwait=0
            [ "$awake" = 1 ] && CUR_BRIGHT=$BRIGHT || CUR_BRIGHT=$SLEEP_BRIGHT
            case $mode in
                worn)     effect=$AWAKE_EFFECT;  ER=$AR;  EG=$AG;  EB=$AB ;;
                charging) effect=$CHARGE_EFFECT; ER=$CHR; EG=$CHG; EB=$CHB ;;
                full)     effect=$FULL_EFFECT;   ER=$FR;  EG=$FG;  EB=$FB ;;
                asleep)   effect=$SLEEP_EFFECT;  ER=$SR;  EG=$SG;  EB=$SB ;;
                low)      effect=$LOW_EFFECT;    ER=$LR;  EG=$LG;  EB=$LB ;;
                boot)     effect=$BOOT_EFFECT;   ER=$OR;  EG=$OG;  EB=$OB; i=0 ;;
            esac
            log "state $mode: $effect"
        fi
        # Asleep on battery the light is only worth showing for a while.
        if [ "$mode" = asleep ] && [ "$SLEEP_TIMEOUT" -gt 0 ] && \
           [ $(( SECONDS - asleep_since )) -ge "$SLEEP_TIMEOUT" ]; then
            effect=off
        fi
    fi
    check=$(( check - 1 ))

    case $effect in
        rainbow)
            h2rgb $(( i * 1536 / frames ))
            emit $r $g $b
            i=$(( i + 1 )); [ "$i" -ge "$frames" ] && i=0
            tick "$FRAME_DELAY"
            ;;
        breathe)
            # Triangle wave over SPEED seconds, floor of 1/16 so it never
            # quite goes out. Gamma (in emit) makes the ramp look smooth.
            _p=$(( i * 512 / frames ))
            [ "$_p" -gt 255 ] && _p=$(( 511 - _p ))
            _p=$(( 16 + _p * 239 / 255 ))
            emit $(( ER * _p / 255 )) $(( EG * _p / 255 )) $(( EB * _p / 255 ))
            i=$(( i + 1 )); [ "$i" -ge "$frames" ] && i=0
            tick "$FRAME_DELAY"
            ;;
        police)
            # Two red flashes, two blue flashes, 0.8 s a cycle whatever FPS.
            case $(( i * 8 / FPS % 8 )) in
                0|2) emit 255 0 0 ;;
                4|6) emit 0 0 255 ;;
                *)   emit 0 0 0 ;;
            esac
            i=$(( i + 1 )); [ "$i" -ge "$FPS" ] && i=0
            tick "$FRAME_DELAY"
            ;;
        battery|solid|off)
            if [ "$effect" = battery ]; then
                [ "$battwait" -le 0 ] && { read_batt; batt_rgb "$BATT"; battwait=20; }
                battwait=$(( battwait - 1 ))
                emit $r $g $b
            elif [ "$effect" = solid ]; then
                emit $ER $EG $EB
            else
                emit 0 0 0
            fi
            # Still lights: rewrite 4 times a second (the light HAL repaints
            # on charge changes) and look at the state every pass.
            tick 0.25
            check=0
            ;;
        *)  emit 0 0 0; tick 0.25; check=0 ;;
    esac
done
