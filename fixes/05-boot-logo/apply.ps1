# Install the boot logo module (swaps /system/etc/meta_logo.png).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device,   # headset as adb sees it, e.g. 192.168.1.50:5555
    [string]$Zip = (Join-Path $PSScriptRoot "05-boot-logo.zip")
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $Zip)) { Write-Host "FAIL: module zip not found at $Zip (download it from the CI build)"; exit 1 }

& $Adb -s $Device push $Zip /sdcard/Download/05-boot-logo.zip
& $Adb -s $Device shell "su -c 'magisk --install-module /sdcard/Download/05-boot-logo.zip'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules_update/quest1_boot_logo/system/etc/meta_logo.png -o -f /data/adb/modules/quest1_boot_logo/system/etc/meta_logo.png && echo yes'"
if ("$ok".Trim() -ne "yes") { Write-Host "FAIL: module did not install (see the output above)"; exit 1 }

Write-Host "Installed; rebooting. Watch the boot animation."
& $Adb -s $Device reboot
