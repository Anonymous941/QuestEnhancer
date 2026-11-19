# Install the systemux-accountless Magisk module (patched SystemUX: dock + accountless Library).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device,   # headset as adb sees it, e.g. 192.168.1.50:5555
    [string]$Zip = (Join-Path $PSScriptRoot "systemux_dock-v7.zip")
)
$ErrorActionPreference = "Stop"
if (-not (Test-Path $Zip)) { Write-Host "FAIL: module zip not found at $Zip (build it with build.sh)"; exit 1 }

# Only for the SystemUX this was built against.
$sum = & $Adb -s $Device shell "su -c 'sha256sum /system/priv-app/SystemUX/SystemUX.apk'"
if ("$sum" -notmatch "^be425390033abd895e8cbded124c658033714cf6ea3512edd71d97d30036c9ed") {
    Write-Host "FAIL: this headset's SystemUX.apk is not the one the module was built from: $sum"; exit 1
}

& $Adb -s $Device push $Zip /sdcard/Download/systemux_dock-v7.zip
& $Adb -s $Device shell "su -c 'magisk --install-module /sdcard/Download/systemux_dock-v7.zip'"
$ok = & $Adb -s $Device shell "su -c 'test -f /data/adb/modules_update/systemux_dock/module.prop -o -f /data/adb/modules/systemux_dock/module.prop && echo yes'"
if ("$ok".Trim() -ne "yes") { Write-Host "FAIL: module did not install (see the output above)"; exit 1 }

Write-Host "Installed; rebooting. If the menus break, run undo.ps1 (adb keeps working)."
& $Adb -s $Device reboot
