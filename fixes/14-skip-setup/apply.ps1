# Skip the first-time setup (NUX) on a rooted Quest 1 (v28-v50), after Juspertinry's
# Monterey Club method. Asks NuxOtaIntentService to skip NUX; the headset reboots
# the next time it is worn.
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
$cmd = 'am startservice -a nux.ota.SKIP_NUX -n com.oculus.nux.ota/com.oculus.nux.ota.NuxOtaIntentService --ez REBOOT true'

$nux = & $Adb -s $Device shell pm list packages com.oculus.nux.ota
if (-not ($nux -match "package:com.oculus.nux.ota$")) {
    Write-Host "FAIL: com.oculus.nux.ota isn't on this firmware (it arrived around v28); this method can't work here"
    exit 1
}

# Root: Magisk's su if it's there, otherwise adbd as root (adb root).
$hasSu = & $Adb -s $Device shell 'command -v su'
if ($hasSu) {
    & $Adb -s $Device shell "su -c '$cmd'"
} else {
    & $Adb -s $Device root
    Start-Sleep -Seconds 3
    & $Adb -s $Device wait-for-device
    & $Adb -s $Device shell $cmd
}
Write-Host "Sent. Put the headset on: it reboots once and should start past setup."
Write-Host "Without a Meta account, Meta's preinstalled apps and the Store won't load; sideloads work."
