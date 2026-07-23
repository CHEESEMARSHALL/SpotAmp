[CmdletBinding()]
param(
    [switch]$SkipBuild,
    [string]$Device = '',
    [switch]$ClearData
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$apk = Join-Path $repoRoot 'app\build\outputs\apk\debug\app-debug.apk'
$packageName = 'com.aistudio.plexmusicplayer.vctplx'
$activityName = "$packageName/com.example.MainActivity"

Push-Location $repoRoot
try {
    $adb = Get-Command adb -ErrorAction SilentlyContinue
    if (-not $adb) { throw 'adb was not found on PATH. Start Android Studio or add the Android SDK platform-tools directory to PATH.' }

    if (-not $SkipBuild -or -not (Test-Path $apk)) {
        $env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
        & .\gradlew.bat --no-daemon --console=plain assembleDebug
        if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE." }
    }

    $adbArgs = @()
    if ($Device) { $adbArgs += @('-s', $Device) }
    $devices = & adb @adbArgs devices
    if (-not ($devices | Select-String '\sdevice$')) {
        throw 'No Android device or emulator is connected. Run adb devices and connect/start one first.'
    }

    if ($ClearData) {
        & adb @adbArgs shell pm clear $packageName
        if ($LASTEXITCODE -ne 0) { throw 'Could not clear the existing SpotAmp app data.' }
    }

    & adb @adbArgs install -r $apk
    if ($LASTEXITCODE -ne 0) { throw 'APK installation failed.' }

    & adb @adbArgs shell am force-stop $packageName
    & adb @adbArgs shell am start -n $activityName
    if ($LASTEXITCODE -ne 0) { throw 'Could not launch SpotAmp.' }

    Write-Host "SpotAmp launched on $($Device ? $Device : 'the connected device')." -ForegroundColor Green
}
finally {
    Pop-Location
}
