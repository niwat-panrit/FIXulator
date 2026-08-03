<#
    Builds the native FIXulator installer for Windows.

    jpackage cannot cross-compile, so this must run on Windows. The MSI type
    additionally needs the WiX Toolset v3 on PATH (https://wixtoolset.org);
    without it, use -Type exe for a self-extracting installer instead.

        .\packaging\jpackage.ps1                 # .msi
        .\packaging\jpackage.ps1 -Type exe       # .exe
        .\packaging\jpackage.ps1 -Type app-image # unpacked folder, no installer
#>
param(
    [string]$Type       = "msi",
    [string]$AppVersion = $(if ($env:APP_VERSION) { $env:APP_VERSION } else { "1.0.0" })
)

$ErrorActionPreference = "Stop"

$AppName     = "FIXulator"
$Vendor      = "Niwat Panrit"
$Description = "FIX protocol simulator for testing - not for production trading"
$MainClass   = "com.npsoftdev.fixsimulator.Main"
$JarName     = "fix-simulator.jar"

$RepoRoot = Split-Path -Parent $PSScriptRoot
$SrcDir   = Join-Path $RepoRoot "src"
$StageDir = Join-Path $SrcDir "target\jpackage-input"
$OutDir   = Join-Path $SrcDir "target\installers"

if (-not (Get-Command jpackage -ErrorAction SilentlyContinue)) {
    throw "jpackage not found - needs JDK 14 or newer on PATH"
}

Write-Host "==> Building $JarName"
Push-Location $SrcDir
# Tests are skipped here: packaging builds from source CI has already tested,
# and Mockito's inline mock maker fails on JDK 21+. Run `mvn test` separately.
try { & mvn -q clean package -DskipTests; if ($LASTEXITCODE -ne 0) { throw "Maven build failed" } }
finally { Pop-Location }

# jpackage copies the whole --input directory into the image, so stage only the JAR.
if (Test-Path $StageDir) { Remove-Item $StageDir -Recurse -Force }
New-Item -ItemType Directory -Path $StageDir, $OutDir -Force | Out-Null
Copy-Item (Join-Path $SrcDir "target\$JarName") $StageDir

# fixulator.packaged tells AppHome to keep runtime data under %LOCALAPPDATA%:
# C:\Program Files is not writable by a standard user.
$jpackageArgs = @(
    "--type",         $Type
    "--name",         $AppName
    "--app-version",  $AppVersion
    "--vendor",       $Vendor
    "--description",  $Description
    "--input",        $StageDir
    "--main-jar",     $JarName
    "--main-class",   $MainClass
    "--dest",         $OutDir
    "--java-options", "-Dfixulator.packaged=true"
    "--java-options", "-Xmx512m"
)

$license = Join-Path $RepoRoot "LICENSE"
if (Test-Path $license) { $jpackageArgs += @("--license-file", $license) }

if ($Type -in @("msi", "exe")) {
    $jpackageArgs += @(
        "--win-dir-chooser"       # let the user pick the install location
        "--win-menu"
        "--win-menu-group",       "FIXulator"
        "--win-shortcut"
        "--win-per-user-install"  # no admin rights required
    )
}

Write-Host "==> jpackage --type $Type"
& jpackage @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage failed with exit code $LASTEXITCODE" }

Write-Host ""
Write-Host "==> Installer in $OutDir"
Get-ChildItem $OutDir
