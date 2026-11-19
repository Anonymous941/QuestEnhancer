# Enable Chats again (it will crash-loop again on this firmware).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
$pkg = "com.oculus.socialplatform"
& $Adb -s $Device shell pm enable $pkg
$disabled = & $Adb -s $Device shell pm list packages -d $pkg
if ($disabled -match "package:$pkg$") { Write-Host "FAIL: $pkg is still disabled"; exit 1 }
else { Write-Host "OK: $pkg enabled" }
