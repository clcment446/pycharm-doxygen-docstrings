# Build the plugin and install it into local PyCharm 2026.2 plugin dirs (Windows).
# Usage: .\install.ps1 [-NoBuild] [-PluginsDir <path>]
param([switch]$NoBuild, [string]$PluginsDir)
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not $NoBuild) {
    $gradleArgs = @("buildPlugin", "--console=plain", "-q")
    if ($env:PYCHARM_HOME -and (Test-Path "$env:PYCHARM_HOME\product-info.json")) {
        $gradleArgs += "-PplatformLocalPath=$env:PYCHARM_HOME"
    }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed" }
}

$zip = Get-ChildItem build\distributions\*.zip | Sort-Object LastWriteTime -Descending | Select-Object -First 1
Write-Host "Plugin: $($zip.FullName)"

$targets = @()
if ($PluginsDir) { $targets += $PluginsDir }
else {
    $found = Get-ChildItem "$env:APPDATA\JetBrains" -Directory -Filter "PyCharm2026.2*" -ErrorAction SilentlyContinue
    foreach ($d in $found) { $targets += "$($d.FullName)\plugins" }
    if (-not $targets) { $targets += "$env:APPDATA\JetBrains\PyCharm2026.2\plugins" }
}

foreach ($t in $targets) {
    New-Item -ItemType Directory -Force -Path $t | Out-Null
    Remove-Item -Recurse -Force "$t\pycharm-doxygen-docstrings" -ErrorAction SilentlyContinue
    Expand-Archive -Force -Path $zip.FullName -DestinationPath $t
    Write-Host "Installed into $t"
}
Write-Host "Restart PyCharm to load the plugin."
