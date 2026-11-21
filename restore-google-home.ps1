# Restore Google TV and remove only Vue TV's Home-button service.
param([Parameter(Mandatory = $true)][string]$Device)
adb connect $Device
$homeServices = (adb -s $Device shell settings get secure enabled_accessibility_services).Trim()
$remainingServices = @($homeServices -split ':' | Where-Object { $_ -and $_ -ne 'null' -and $_ -ne 'je.vu.tvhome/je.vu.tvhome.HomeButtonService' -and $_ -ne 'je.vu.tvhome/.HomeButtonService' })
if ($remainingServices.Count -gt 0) {
    adb -s $Device shell settings put secure enabled_accessibility_services ($remainingServices -join ':')
} else {
    adb -s $Device shell settings delete secure enabled_accessibility_services
    adb -s $Device shell settings put secure accessibility_enabled 0
}
adb -s $Device shell cmd package set-home-activity --user 0 com.google.android.apps.tv.launcherx/.home.HomeActivity
adb -s $Device shell am start -a android.intent.action.MAIN -c android.intent.category.HOME
