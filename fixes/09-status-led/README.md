# 09: Status LED effects

Magisk module that drives the Quest 1's RGB status LED. Based on
[Juspertinry's q1_led v1.0.1](https://github.com/Juspertinry/q1-modules/tree/main/q1_led)
(MIT, licence in `module/LICENSE`). Module id stays `questled`, so it replaces q1_led
if you have it, and keeps your `/data/adb/questled.conf`.

| | q1_led | this |
|---|---|---|
| States | worn: an effect; asleep: battery colour | worn, **charging**, **charged** (still plugged in) and asleep on battery, each with its own effect and colour; charging can show while worn too; **low battery** (asleep on battery at or below a %, no timeout) and a **boot finished** animation |
| Effects | rainbow, battery, off | also **solid**, **breathe** and **police** (red/blue); any colour (#RRGGBB) |
| Changing settings | edit the conf, restart the daemon | `questled` command or the QuestEnhancer Manager (presets or a colour wheel); applies at once |
| `ENABLED=0` | daemon exits until reboot | LED goes dark, daemon waits, turns back on live |

Idea credits (no code copied, those repos have no licence): mlsplays' LED colour
control/cycle modules and the Police LED idea from the Monterey Club Discord.

## Install

Flash `09-status-led-rN.zip` (from the CI artifact `quest1-fixes-rN-<commit>`) in Magisk, restart.

## Use

    su -c 'questled'                       # every setting, and whether the daemon runs
    su -c 'questled effect breathe'        # while worn
    su -c 'questled color #FF6000'
    su -c 'questled charge solid #FF6000'  # while charging
    su -c 'questled full solid #00FF40'    # charged, still plugged in
    su -c 'questled sleep battery'         # asleep on battery
    su -c 'questled chargeworn on'         # charging colours while worn too
    su -c 'questled sleeptimeout 600'      # dark after 10 min asleep on battery (0 never)
    su -c 'questled lowwarn on'            # low battery warning (off by default)
    su -c 'questled low breathe #FF0000'   # its effect and colour
    su -c 'questled lowlevel 15'           # at or below 15%
    su -c 'questled bootanim on'           # boot finished animation (off by default)
    su -c 'questled boot rainbow'          # its effect
    su -c 'questled bootsecs 4'            # for 4 s
    su -c 'questled off'                   # LED dark; 'questled on' brings it back
    su -c 'questled help'

Effects: rainbow, battery (red, orange, green by charge), solid, breathe, police, off.
The command writes `/data/adb/questled.conf` and sends the daemon `SIGUSR1`; the
daemon also re-reads the conf by itself when the file is newer, for hand edits.

## Remove

Uninstall in Magisk: the LED is turned off and handed back to the system;
`/data/adb/questled.conf` stays for a reinstall.

## Status

States, effects and live reload tested on a PC with fake LED and battery files (bash). Not yet on
the headset (Android's mksh), 2026-11-19.
