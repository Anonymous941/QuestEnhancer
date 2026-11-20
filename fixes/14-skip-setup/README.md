# 14: Skip first-time setup (NUX)

After [Juspertinry's Monterey Club method](../../CREDITS.md) ("Factory setup skip
(bypass NUX)"). For a rooted Quest 1 that has just been factory reset, to get past
Meta's setup with no phone app and no account.

`apply.ps1 -Device ...` sends

    am startservice -a nux.ota.SKIP_NUX -n com.oculus.nux.ota/com.oculus.nux.ota.NuxOtaIntentService --ez REBOOT true

as root (Magisk's `su`, or `adb root` if there's no su). Wear the headset once
afterwards: it reboots and should start past setup.

What the thread reports (none of it tested on our headset yet):

- Works on v50; `com.oculus.nux.ota` arrived around v28, so older firmware fails
  ("inaccessible or not found" on v7, a boot loop tried on v12).
- With no Meta account, Meta's preinstalled apps and the Store don't load; sideloads
  install and run. To add an account later: the Meta Horizon phone app, your
  headset, "Finish setup" (an older app version, around 212, was needed).
- It doesn't change updates or adb.

This is a script, not a Manager button: the Manager can't run until setup is done.
There's no undo; a factory reset brings setup back.
