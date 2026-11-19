# Remove the boot logo module (stock Meta logo after a reboot).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
& $Adb -s $Device shell "su -c 'touch /data/adb/modules/quest1_boot_logo/remove'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules/quest1_boot_logo/remove && echo yes'"
if ("$ok".Trim() -eq "yes") { Write-Host "OK: module marked for removal; rebooting"; & $Adb -s $Device reboot }
else { Write-Host "FAIL: module not found"; exit 1 }
