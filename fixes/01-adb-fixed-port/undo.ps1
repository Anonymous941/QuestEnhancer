# Clear the fixed adb port. adb is USB only again after a reboot.
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
& $Adb -s $Device shell "su -c 'setprop persist.adb.tcp.port \"\"'"
$v = (& $Adb -s $Device shell getprop persist.adb.tcp.port).Trim()
if ($v -eq "") { Write-Host "OK: property cleared; reboot to finish" }
else { Write-Host "FAIL: property is still '$v'"; exit 1 }
