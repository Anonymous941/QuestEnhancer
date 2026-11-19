# Install the Magisk module that stops Android Developer options crashing.
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device,   # headset as adb sees it, e.g. 192.168.1.50:5555
    [string]$Zip = (Join-Path $PSScriptRoot "03-devsettings-selinux.zip")
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $Zip)) { Write-Host "FAIL: module zip not found at $Zip (download it from the CI build)"; exit 1 }

& $Adb -s $Device push $Zip /sdcard/Download/03-devsettings-selinux.zip
& $Adb -s $Device shell "su -c 'magisk --install-module /sdcard/Download/03-devsettings-selinux.zip'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules_update/quest1_devsettings_fix/sepolicy.rule -o -f /data/adb/modules/quest1_devsettings_fix/sepolicy.rule && echo yes'"
if ("$ok".Trim() -ne "yes") { Write-Host "FAIL: module did not install (see the output above)"; exit 1 }

Write-Host "Installed; rebooting..."
& $Adb -s $Device reboot
Write-Host "When the headset is back, open the page with:"
Write-Host "  $Adb connect $Device"
Write-Host "  $Adb -s $Device shell `"am start -n 'com.android.settings/.Settings``$DevelopmentSettingsDashboardActivity'`""
