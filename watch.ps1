# Set up environment variables locally for Android/Gradle toolchain
$env:JAVA_HOME        = "C:\Program Files\Microsoft\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME     = "$PSScriptRoot\android-sdk"
$env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
$env:GRADLE_USER_HOME = "$PSScriptRoot\.gradle_user_home"
$adb                  = "$PSScriptRoot\android-sdk\platform-tools\adb.exe"

# ---------------------------------------------------------------
#  UI Primitives
# ---------------------------------------------------------------

$spinnerFrames = @("⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏")
$script:spinIdx = 0

function Get-Timestamp { return (Get-Date -Format "hh:mm:ss tt") }

function Write-Header {
    Clear-Host
    Write-Host ""
    # Outer border — 63 chars wide inner
    Write-Host "    ╭───────────────────────────────────────────────────────────────╮" -ForegroundColor DarkCyan
    Write-Host "    │                                                               │" -ForegroundColor DarkCyan
    # Row: bubble top
    Write-Host "    │       ╭──────────────╮   CHAT APP  ·  Dev Build System        │" -ForegroundColor DarkCyan
    # Row: bubble body
    Write-Host "    │      " -NoNewline -ForegroundColor DarkCyan
    Write-Host "[ " -NoNewline -ForegroundColor DarkYellow
    Write-Host "●" -NoNewline -ForegroundColor Yellow
    Write-Host " " -NoNewline -ForegroundColor DarkYellow
    Write-Host "●" -NoNewline -ForegroundColor Yellow
    Write-Host " " -NoNewline -ForegroundColor DarkYellow
    Write-Host "●" -NoNewline -ForegroundColor Yellow
    Write-Host " ]  " -NoNewline -ForegroundColor DarkYellow
    Write-Host "                                                  │" -ForegroundColor DarkCyan
    # Row: bubble bottom + tail
    Write-Host "    │       ╰──────────────╯                                        │" -ForegroundColor DarkCyan
    Write-Host "    │            ╲╱                                                 │" -ForegroundColor DarkCyan
    Write-Host "    │                                                               │" -ForegroundColor DarkCyan
    Write-Host "    ╰───────────────────────────────────────────────────────────────╯" -ForegroundColor DarkCyan
    Write-Host ""
}

function Write-Section($label) {
    Write-Host ""
    Write-Host "  ┌─ " -NoNewline -ForegroundColor DarkGray
    Write-Host $label -NoNewline -ForegroundColor White
    Write-Host (" " + ("─" * [Math]::Max(1, 54 - $label.Length)) + "┐") -ForegroundColor DarkGray
}

function Write-Log($icon, $msg, $color = "Gray") {
    Write-Host "  │  " -NoNewline -ForegroundColor DarkGray
    Write-Host "$icon " -NoNewline -ForegroundColor $color
    Write-Host $msg -ForegroundColor $color
}

function Write-OK($msg)      { Write-Log "✔" $msg "Green" }
function Write-Info($msg)    { Write-Log "→" $msg "Cyan" }
function Write-Warn($msg)    { Write-Log "!" $msg "Yellow" }
function Write-Fail($msg)    { Write-Log "✘" $msg "Red" }
function Write-Dim($msg)     { Write-Log "·" $msg "DarkGray" }

function Write-StatusLine($label, $value, $valueColor = "White") {
    Write-Host "  │  " -NoNewline -ForegroundColor DarkGray
    Write-Host ("{0,-18}" -f $label) -NoNewline -ForegroundColor DarkGray
    Write-Host $value -ForegroundColor $valueColor
}

# ---------------------------------------------------------------
#  Device Helpers
# ---------------------------------------------------------------

$script:deviceCache = @{}

function Get-ActiveDevice {
    $devices    = & $adb devices 2>&1
    $deviceList = @()
    foreach ($line in $devices) {
        if ($line -match "^(\S+)\s+device(\s+|$)") {
            $id = $matches[1]
            if ($id -notlike "emulator-*") {
                $deviceList += $id
            }
        }
    }
    return $deviceList
}

function Get-DeviceLabel($id) {
    if ($script:deviceCache.ContainsKey($id)) {
        return $script:deviceCache[$id]
    }
    
    $modelRaw = & $adb -s $id shell getprop ro.product.model 2>&1
    if ($modelRaw -is [array]) { $modelRaw = $modelRaw[0] }
    $model = "$modelRaw".Trim()
    
    $brandRaw = & $adb -s $id shell getprop ro.product.manufacturer 2>&1
    if ($brandRaw -is [array]) { $brandRaw = $brandRaw[0] }
    $brand = "$brandRaw".Trim()
    
    $icon = "📱"
    
    $label = "$id  $icon  Phone"
    if ($brand -and $model -and $brand -notmatch "error|adb" -and $model -notmatch "error|adb") {
        $brandFormatted = (Get-Culture).TextInfo.ToTitleCase($brand.ToLower())
        $label = "$id  $icon  $brandFormatted $model"
    }
    
    $script:deviceCache[$id] = $label
    return $label
}

# ---------------------------------------------------------------
#  Build & Install
# ---------------------------------------------------------------

function BuildAndInstall {
    # ── Devices ──────────────────────────────────────────────────
    Write-Section "BUILD  &  DEPLOY"
    $deviceIds = @(Get-ActiveDevice)
    if ($deviceIds.Count -eq 0) {
        Write-Fail "No active devices connected."
        Write-Dim  "Please connect your phone via USB Debugging and try again."
        return
    }
    Write-StatusLine "Target Devices:" "$($deviceIds.Count) device(s)" "Cyan"
    foreach ($d in $deviceIds) { Write-Dim (Get-DeviceLabel $d) }

    # ── Build ─────────────────────────────────────────────────────
    Write-Host ""
    Write-Info "Running assembleDebug..."
    $startTime   = Get-Date
    $buildResult = Start-Process -FilePath ".\gradlew.bat" -ArgumentList "assembleDebug" -NoNewWindow -PassThru -Wait
    $duration    = ((Get-Date) - $startTime).TotalSeconds

    if ($buildResult.ExitCode -ne 0) {
        Write-Fail "Build failed  (exit $($buildResult.ExitCode))"
        return
    }
    Write-OK ("Build succeeded  ›  {0:N1}s" -f $duration)

    # ── Install ───────────────────────────────────────────────────
    $apkPath = "app\build\outputs\apk\debug\app-debug.apk"
    if (-not (Test-Path $apkPath)) {
        Write-Fail "APK not found: $apkPath"
        return
    }

    Write-Host ""
    foreach ($targetDevice in $deviceIds) {
        $label = Get-DeviceLabel $targetDevice
        Write-Info "Installing on $label..."
        # Forward telemetry port 8088 from device to PC
        & $adb -s $targetDevice reverse tcp:8088 tcp:8088 2>&1 | Out-Null
        $out = & $adb -s $targetDevice install -r $apkPath 2>&1
        if ($out -match "Success") {
            Write-OK "Installed  ›  $label"
            $launchOut = & $adb -s $targetDevice shell monkey -p com.chat.app -c android.intent.category.LAUNCHER 1 2>&1
            if ($launchOut -match "error|exception" -and $launchOut -notmatch "Events injected") {
                Write-Warn "Launch failed  ›  $label"
            } else {
                Write-OK "Launched  ›  $label"
            }
        } else {
            Write-Fail "Install failed  ›  $label"
            $out | ForEach-Object { Write-Dim $_ }
        }
    }
}

# ---------------------------------------------------------------
#  Entry Point
# ---------------------------------------------------------------

Write-Header

# Initial build
BuildAndInstall

# ── Watcher ──────────────────────────────────────────────────────
Write-Section "WATCHER"
Write-StatusLine "Status:" "Active" "Green"
Write-StatusLine "Debounce:" "2.5s" "Gray"
Write-Dim "Ctrl+C to stop"

$watcher = New-Object System.IO.FileSystemWatcher
$watcher.Path = $PSScriptRoot
$watcher.IncludeSubdirectories = $true
$watcher.EnableRaisingEvents   = $true

$lastEventTime   = [DateTime]::MinValue
$debounceSeconds = 2.5
$changeIdx       = 0

while ($true) {
    $change = $watcher.WaitForChanged([System.IO.WatcherChangeTypes]::All, 1000)
    if ($change.TimedOut -eq $false) {
        if ($change.Name -match "build|\.gradle|\.git|watch\.ps1|build\.log|android-sdk|\.gradle_user_home|Loger|node_modules") {
            continue
        }
        $now = Get-Date
        if (($now - $lastEventTime).TotalSeconds -ge $debounceSeconds) {
            $lastEventTime = $now
            $changeIdx++
            Write-Host ""
            $changeHeader = "  ●●● CHANGE #$changeIdx  ›  $($change.Name)"
            $ts = "  $(Get-Timestamp)  "
            $totalWidth = 66
            $spacer = " " * [Math]::Max(1, $totalWidth - $changeHeader.Length - $ts.Length)
            Write-Host "  ╔" -NoNewline -ForegroundColor DarkYellow
            Write-Host ("═" * 64) -NoNewline -ForegroundColor DarkYellow
            Write-Host "╗" -ForegroundColor DarkYellow
            Write-Host "  ║  " -NoNewline -ForegroundColor DarkYellow
            Write-Host "●" -NoNewline -ForegroundColor Yellow
            Write-Host "●" -NoNewline -ForegroundColor DarkYellow
            Write-Host "● " -NoNewline -ForegroundColor Yellow
            Write-Host "CHANGE #$changeIdx" -NoNewline -ForegroundColor White
            Write-Host "  ›  " -NoNewline -ForegroundColor DarkGray
            Write-Host $($change.Name) -NoNewline -ForegroundColor Yellow
            Write-Host $spacer -NoNewline
            Write-Host $ts -NoNewline -ForegroundColor DarkGray
            Write-Host "║" -ForegroundColor DarkYellow
            Write-Host "  ╚" -NoNewline -ForegroundColor DarkYellow
            Write-Host ("═" * 64) -NoNewline -ForegroundColor DarkYellow
            Write-Host "╝" -ForegroundColor DarkYellow
            BuildAndInstall
            Write-Section "WATCHER"
            Write-Dim "Watching for changes..."
        }
    }
}
