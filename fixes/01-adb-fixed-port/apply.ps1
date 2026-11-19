# Wireless adb on a fixed port (5555). Run from the platform-tools folder.
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Ip,   # headset Wi-Fi address
    [string]$Serial = ""      # USB serial if more than one device is attached
)
$ErrorActionPreference = "Stop"
$s = @(); if ($Serial) { $s = @("-s", $Serial) }

Write-Host "Setting persist.adb.tcp.port=5555 as root..."
& $Adb @s shell "su -c 'setprop persist.adb.tcp.port 5555'"
$v = (& $Adb @s shell getprop persist.adb.tcp.port).Trim()
if ($v -ne "5555") { Write-Host "FAIL: property is '$v' (is root granted to shell in Magisk?)"; exit 1 }

Write-Host "Restarting adbd on 5555..."
& $Adb @s tcpip 5555
Start-Sleep 3
& $Adb connect "${Ip}:5555"
$state = (& $Adb -s "${Ip}:5555" get-state 2>&1)
if ("$state".Trim() -eq "device") { Write-Host "OK: connected to ${Ip}:5555" }
else { Write-Host "FAIL: could not connect to ${Ip}:5555 ($state)"; exit 1 }
