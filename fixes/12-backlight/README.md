# 12: Display brightness pin

Our own Magisk module, after the Discord tip of writing to
`/sys/class/leds/lcd-backlight/brightness` (its module pinned it at 255).
Module id `quest1_backlight`.

A small daemon holds the backlight at the value you choose while the display is
on. It never writes while the backlight is 0 (display off), so it can't light up a
sleeping panel. Off by default.

    su -c 'qebacklight'             # now, max, pin, value
    su -c 'qebacklight value 200'   # 1-255
    su -c 'qebacklight pin on'      # applies within 2 s, no restart
    su -c 'qebacklight pin off'     # the system's own brightness again

Or the QuestEnhancer Manager (Display). A brighter panel runs warmer and an OLED
ages faster at high brightness; the system's own value is the safe choice.

## Status

Not yet on the headset, 2026-11-19.
