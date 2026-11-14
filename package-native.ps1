param (
    [string]$PackageType = "msi",
    [string]$AppVersion = "1.0.4"
)

$ErrorActionPreference = "Stop"
$ProjectDir = $PSScriptRoot
Set-Location $ProjectDir

# Nettoyage de la version (ex: "v1.0.0" -> "1.0.0", "1.0.0-SNAPSHOT" -> "1.0.0")
$CleanVersion = $AppVersion.TrimStart("v").Split("-")[0]

Write-Host "=== [1/4] Détection de jpackage et du JDK ===" -ForegroundColor Cyan

$JpackageCmd = $null
$FoundCmd = Get-Command jpackage -ErrorAction SilentlyContinue
if ($FoundCmd) {
    $JpackageCmd = $FoundCmd.Source
}
if (-not $JpackageCmd -and $env:JAVA_HOME) {
    $Candidate = Join-Path $env:JAVA_HOME "bin\jpackage.exe"
    if (Test-Path $Candidate) {
        $JpackageCmd = $Candidate
    }
}

if (-not $JpackageCmd) {
    Write-Error "ERREUR : 'jpackage' n'a pas été trouvé. Assurez-vous d'avoir installé un JDK 25/27+ et configuré JAVA_HOME."
    exit 1
}

# Détection automatique de WiX Toolset pour les installateurs MSI / EXE
if ($PackageType -eq "msi" -or $PackageType -eq "exe") {
    $WixCandidates = @(
        "C:\Program Files (x86)\WiX Toolset v3.11\bin",
        "C:\Program Files (x86)\WiX Toolset v3.14\bin",
        "C:\Program Files\WiX Toolset v3.11\bin"
    )
    foreach ($Cand in $WixCandidates) {
        if (Test-Path (Join-Path $Cand "candle.exe")) {
            $env:PATH = "$Cand;$env:PATH"
            Write-Host "WiX Toolset détecté et ajouté au PATH : $Cand" -ForegroundColor Green
            break
        }
    }
}

Write-Host "Utilisation de jpackage : $JpackageCmd"
Write-Host "Type de paquet          : $PackageType"
Write-Host "Version                 : $CleanVersion"

Write-Host "=== [2/4] Préparation de la distribution Gradle ===" -ForegroundColor Cyan
$InputDir = "build\install\Grafino\lib"
if (-not (Test-Path $InputDir) -or $env:SKIP_BUILD -ne "true") {
    if (Test-Path ".\gradlew.bat") {
        .\gradlew.bat installDist "-PappVersion=$CleanVersion" -x test
    } else {
        gradle installDist "-PappVersion=$CleanVersion" -x test
    }
}

Write-Host "=== [3/4] Vérification des artefacts d'entrée ===" -ForegroundColor Cyan
if (-not (Test-Path $InputDir)) {
    Write-Error "ERREUR : Répertoire introuvable : $InputDir"
    exit 1
}

$JarFiles = Get-ChildItem -Path "$InputDir\Grafino-*.jar" -ErrorAction SilentlyContinue
if (-not $JarFiles) {
    Write-Error "ERREUR : Aucun fichier Grafino-*.jar trouvé dans $InputDir"
    exit 1
}
$MainJar = ($JarFiles | Select-Object -First 1).Name
Write-Host "Archive principale détectée : $MainJar"

$DistDir = "dist"
if (-not (Test-Path $DistDir)) { New-Item -ItemType Directory -Path $DistDir | Out-Null }

# Nettoyage préventif du bundle existant pour éviter les conflits
if (Test-Path "$DistDir\Grafino") {
    Remove-Item -Recurse -Force "$DistDir\Grafino"
}

$IconOpt = @()
if (Test-Path "src\main\resources\icons\app-icon.ico") {
    $IconPath = (Resolve-Path "src\main\resources\icons\app-icon.ico").Path
    $IconOpt = @("--icon", $IconPath)
}

$ExtraOpts = @()
if ($PackageType -ne "app-image") {
    $ExtraOpts = @("--win-menu", "--win-shortcut", "--win-dir-chooser")
}

Write-Host "=== [4/4] Création du paquet natif ($PackageType) avec jpackage ===" -ForegroundColor Cyan

$JpackageArgs = @(
    "--name", "Grafino",
    "--app-version", $CleanVersion,
    "--vendor", "TAHRI Khalid",
    "--description", "Grafino - SFC and Grafcet Editor & Simulator (CEI 60848 & CEI 61131-3)",
    "--input", (Resolve-Path $InputDir).Path,
    "--main-jar", $MainJar,
    "--main-class", "tk.tahri.grafino.Main",
    "--dest", (Resolve-Path $DistDir).Path,
    "--type", $PackageType,
    "--java-options", "--enable-native-access=ALL-UNNAMED",
    "--java-options", "-Dprism.verbose=false",
    "--verbose"
) + $IconOpt + $ExtraOpts

& $JpackageCmd @JpackageArgs
if ($LASTEXITCODE -ne 0) {
    Write-Error "Échec de jpackage (code d'erreur $LASTEXITCODE)"
    exit $LASTEXITCODE
}

Write-Host "==============================================================================" -ForegroundColor Green
Write-Host " Packaging Windows terminé avec succès !" -ForegroundColor Green
Write-Host " Emplacement : $ProjectDir\$DistDir" -ForegroundColor Green
Write-Host "==============================================================================" -ForegroundColor Green
