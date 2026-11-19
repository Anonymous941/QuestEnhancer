# Keep Meta's app auto-updates off: QuestEnhancer hook on OCMS, then OCMS back on.
# Needs the QuestEnhancer build with OcmsUpdateHooks installed first.
# Run from the folder that holds adb.exe (or pass -Adb).
param(
    [string]$Adb = ".\adb.exe",
    [Parameter(Mandatory = $true)][string]$Device   # headset as adb sees it, e.g. 192.168.1.50:5555
)
$ErrorActionPreference = "Stop"
$here = Split-Path $MyInvocation.MyCommand.Path
& $Adb -s $Device push (Join-Path $here "ocms-scope.sh") /data/local/tmp/ocms-scope.sh | Out-Host
& $Adb -s $Device shell "su -c 'sh /data/local/tmp/ocms-scope.sh add'"
& $Adb -s $Device shell "su -c 'pm enable com.oculus.ocms'"
& $Adb -s $Device shell "su -c 'am force-stop com.oculus.ocms'"
& $Adb -s $Device shell pm list packages -d com.oculus.ocms
Write-Host "(no line above = OCMS enabled)"
Write-Host "Open the Store once in the headset so OCMS starts with the hook, then run check:"
Write-Host "  run fixes\06-questenhancer\qe-check.sh as in its README"
Write-Host "and look for: QuestEnhancer/OcmsUpdates: auto-updates blocked"
