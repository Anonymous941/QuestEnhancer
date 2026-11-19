# Take the OCMS hook out again (OCMS stays enabled). Run from the folder that holds adb.exe (or pass -Adb).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
$here = Split-Path $MyInvocation.MyCommand.Path
& $Adb -s $Device push (Join-Path $here "ocms-scope.sh") /data/local/tmp/ocms-scope.sh | Out-Host
& $Adb -s $Device shell "su -c 'sh /data/local/tmp/ocms-scope.sh remove'"
& $Adb -s $Device shell "su -c 'am force-stop com.oculus.ocms'"
Write-Host "OK: hook removed; OCMS auto-updates follow the account setting again"
