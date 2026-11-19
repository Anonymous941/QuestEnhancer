# 09: Status LED effects

Magisk module that drives the Quest 1's RGB status LED. Based on
[Juspertinry's q1_led v1.0.1](https://github.com/Juspertinry/q1-modules/tree/main/q1_led)
(MIT, licence in `module/LICENSE`). Module id stays `questled`, so it replaces q1_led
if you have it, and keeps your `/data/adb/questled.conf`.

| | q1_led | this |
|---|---|---|
| While worn | rainbow, battery, off | also **solid** colour, **breathe** and **police** (red/blue) |
| While asleep | battery colour, blanks after 90 s on battery | same |
| Changing settings | edit the conf, restart the daemon | `questled` command or the QuestEnhancer Manager; applies within a second |
| `ENABLED=0` | daemon exits until reboot | LED goes dark, daemon waits, turns back on live |

Idea credits (no code copied, those repos have no licence): mlsplays' LED colour
control/cycle modules and the Police LED idea from the Monterey Club Discord.

## Install

Flash `09-status-led.zip` (from the CI artifact `quest1-fixes-N`) in Magisk, restart.

## Use

    su -c 'questled'                  # settings, and whether the daemon runs
    su -c 'questled effect breathe'   # rainbow battery solid breathe police off
    su -c 'questled color #FF6000'
    su -c 'questled speed 4'          # seconds per rainbow turn or breath
    su -c 'questled bright 120'       # 0-255
    su -c 'questled off'              # LED dark; 'questled on' to bring it back
    su -c 'questled log'

The command writes the setting and sends the daemon `SIGUSR1`; the daemon also
re-reads the conf by itself when the file is newer, for hand edits.

## Remove

Uninstall in Magisk: the LED is turned off and handed back to the system;
`/data/adb/questled.conf` stays for a reinstall.

## Status

Effects and live reload tested on a PC with fake LED files (bash). Not yet on
the headset (Android's mksh), 2026-11-19.
