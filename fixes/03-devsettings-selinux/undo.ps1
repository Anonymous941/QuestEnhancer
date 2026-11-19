# Remove the Developer options SELinux module (takes effect after a reboot).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
& $Adb -s $Device shell "su -c 'touch /data/adb/modules/quest1_devsettings_fix/remove'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules/quest1_devsettings_fix/remove && echo yes'"
if ("$ok".Trim() -eq "yes") { Write-Host "OK: module marked for removal; reboot to finish" }
else { Write-Host "FAIL: module not found"; exit 1 }
