# Script PowerShell per generare il Keystore di Release e configurare keystore.properties

$ErrorActionPreference = "Stop"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "  Generatore Chiave di Firma Android (ABlob)  " -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan
Write-Host ""

# Cerca keytool
$keytoolPath = "keytool"
$keytoolExists = $null -ne (Get-Command "keytool" -ErrorAction SilentlyContinue)

if (-not $keytoolExists) {
    # Cerca nei percorsi di installazione tipici di Android Studio
    $commonPaths = @(
        "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe",
        "C:\Program Files\Android\Android Studio\jre\bin\keytool.exe"
    )
    foreach ($path in $commonPaths) {
        if (Test-Path $path) {
            $keytoolPath = $path
            $keytoolExists = $true
            break
        }
    }
}

if (-not $keytoolExists) {
    Write-Host "[ERRORE] 'keytool' non è stato trovato nel PATH nè nei percorsi comuni di Android Studio." -ForegroundColor Red
    Write-Host "Assicurati che Android Studio sia installato o che Java JDK sia nel PATH di sistema." -ForegroundColor Red
    Exit 1
}

$keystoreFile = "release.jks"
$propertiesFile = "keystore.properties"

if (Test-Path $keystoreFile) {
    $choice = Read-Host "Il file '$keystoreFile' esiste già nella directory corrente. Sovrascriverlo? (s/N)"
    if ($choice -ne "s" -and $choice -ne "S") {
        Write-Host "Operazione annullata." -ForegroundColor Yellow
        Exit 0
    }
    Remove-Item $keystoreFile -Force
}

# Input password
$securePassword = Read-Host -AsSecureString "Inserisci una password sicura per il Keystore (minimo 6 caratteri)"
$password = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword))

if ($password.Length -lt 6) {
    Write-Host "[ERRORE] La password deve contenere almeno 6 caratteri." -ForegroundColor Red
    Exit 1
}

$securePasswordConfirm = Read-Host -AsSecureString "Conferma la password del Keystore"
$passwordConfirm = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto([System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePasswordConfirm))

if ($password -ne $passwordConfirm) {
    Write-Host "[ERRORE] Le password non corrispondono." -ForegroundColor Red
    Exit 1
}

$alias = "release-key"
Write-Host ""
Write-Host "Generazione del Keystore '$keystoreFile' in corso..." -ForegroundColor Yellow

$dname = "CN=ABlob Release, O=Alberto Baroni, C=IT"

# Esecuzione di keytool per la generazione
& $keytoolPath -genkeypair -v -keystore $keystoreFile -alias $alias -keyalg RSA -keysize 2048 -validity 10000 -storepass $password -keypass $password -dname $dname

if (Test-Path $keystoreFile) {
    Write-Host ""
    Write-Host "[OK] Chiave generata correttamente in: $keystoreFile" -ForegroundColor Green
    
    # Scrittura del file keystore.properties senza UTF-8 BOM
    $propertiesContent = "storePassword=$password`nkeyPassword=$password`nkeyAlias=$alias`nstoreFile=$keystoreFile`n"
    $absolutePropertiesPath = Join-Path (Get-Location).Path $propertiesFile
    [System.IO.File]::WriteAllText($absolutePropertiesPath, $propertiesContent)
    Write-Host "[OK] File '$propertiesFile' creato con successo." -ForegroundColor Green
    
    Write-Host ""
    Write-Host "================== AVVISO IMPORTANTE ==================" -ForegroundColor Yellow
    Write-Host "1. I file '$keystoreFile' e '$propertiesFile' sono nel tuo .gitignore." -ForegroundColor Yellow
    Write-Host "   Non verranno mai tracciati o caricati su GitHub per motivi di sicurezza." -ForegroundColor Yellow
    Write-Host "2. EFFETTUA UN BACKUP della chiave '$keystoreFile' in un luogo sicuro." -ForegroundColor Yellow
    Write-Host "   Se perdi o cancelli questo file, NON potrai più caricare aggiornamenti" -ForegroundColor Red
    Write-Host "   della tua app sul Play Store in futuro." -ForegroundColor Red
    Write-Host "=======================================================" -ForegroundColor Yellow
} else {
    Write-Host "[ERRORE] Si è verificato un errore durante la generazione della chiave." -ForegroundColor Red
}
