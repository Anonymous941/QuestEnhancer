# Disable Chats (com.oculus.socialplatform), which crash-loops on Quest 1 firmware.
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
$pkg = "com.oculus.socialplatform"

$installed = & $Adb -s $Device shell pm list packages $pkg
if (-not ($installed -match "package:$pkg$")) {
    Write-Host "$pkg is not installed for user 0; restoring it first (never leave it uninstalled)..."
    & $Adb -s $Device shell cmd package install-existing $pkg
}
& $Adb -s $Device shell pm disable-user --user 0 $pkg
$disabled = & $Adb -s $Device shell pm list packages -d $pkg
if ($disabled -match "package:$pkg$") { Write-Host "OK: $pkg disabled; reboot to clear any running loop" }
else { Write-Host "FAIL: $pkg is not disabled"; exit 1 }
