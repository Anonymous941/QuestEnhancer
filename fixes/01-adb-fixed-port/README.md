# 01: Wireless adb on a fixed port

**Problem.** Android 10's wireless debugging picks a new port every time it
starts and needs pairing, and the stock developer settings that turn it on
crashed (see fix 03), so every session began with PrivateQuest and a new
pairing code.

**Fix.** Set `persist.adb.tcp.port=5555` as root. adbd reads it at boot and
listens on 5555 with no pairing (`ro.adb.secure=0` on this headset).

**Status.** Confirmed on the headset 2026-11-05: after a reboot,
`adb connect` to the headset on port 5555 worked with no app and no pairing.

**Apply.** The first time, the headset must be reachable by adb somehow
(USB is simplest). `apply.ps1` sets the property, restarts adbd on 5555 and
connects. If 5555 is ever refused: plug in USB and run
`.\adb -s USB_SERIAL tcpip 5555` (the serial from `.\adb devices`).

**Undo.** `undo.ps1` clears the property; after a reboot adb is USB only
again.

**Security.** Anyone on the same Wi-Fi can connect to adb on 5555 while it
is on. Use it on a trusted home network.
