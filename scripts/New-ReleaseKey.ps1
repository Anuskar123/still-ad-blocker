param([string]$Keytool = 'keytool')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$signingDirectory = Join-Path $projectRoot 'signing'
$keyPath = Join-Path $signingDirectory 'still-release.jks'
$propertiesPath = Join-Path $signingDirectory 'release.properties'
if ((Test-Path -LiteralPath $keyPath) -or (Test-Path -LiteralPath $propertiesPath)) {
    throw 'Signing material already exists. It will not be overwritten.'
}
New-Item -ItemType Directory -Path $signingDirectory -Force | Out-Null
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $signingDirectory /inheritance:r /grant:r "${identity}:(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' '*S-1-5-32-544:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict signing directory permissions.' }
$random = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Fill($random)
$env:STILL_KEY_PASSWORD = [Convert]::ToBase64String($random)
try {
    & $Keytool -genkeypair -noprompt -keystore $keyPath -storetype JKS -alias still -keyalg RSA -keysize 2048 -validity 10000 -storepass:env STILL_KEY_PASSWORD -keypass:env STILL_KEY_PASSWORD -dname 'CN=Still Android Release'
    if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    @("storeFile=signing/still-release.jks", "storePassword=$env:STILL_KEY_PASSWORD", 'keyAlias=still', "keyPassword=$env:STILL_KEY_PASSWORD") | Set-Content -LiteralPath $propertiesPath -Encoding ascii
} finally { Remove-Item Env:STILL_KEY_PASSWORD -ErrorAction SilentlyContinue }
Write-Output 'Release signing material created in signing/. Back up this folder securely. Do not publish it.'
