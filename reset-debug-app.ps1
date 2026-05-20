$packageName = "com.example.unmarkeddetector"

Write-Host "Resetting $packageName to fresh-install state..."

adb uninstall $packageName | Out-Host

Write-Host ""
Write-Host "App uninstalled."
Write-Host "Run .\\gradlew installDebug to install it again."
Write-Host "After reinstall, permissions, Room DB, SharedPreferences and onboarding will be reset."
