# Remove the systemux-accountless module (stock SystemUX comes back after a reboot).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
& $Adb -s $Device shell "su -c 'touch /data/adb/modules/systemux_dock/remove'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules/systemux_dock/remove && echo yes'"
if ("$ok".Trim() -eq "yes") { Write-Host "OK: module marked for removal; rebooting"; & $Adb -s $Device reboot }
else { Write-Host "FAIL: module not found"; exit 1 }
