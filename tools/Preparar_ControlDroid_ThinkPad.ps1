#requires -Version 5.1
<#!
.SYNOPSIS
  Prepara ADB y scrcpy en Windows para controlar un Android conectado por USB.
.DESCRIPTION
  Descarga Android Platform Tools desde Google y scrcpy desde la publicación oficial
  de Genymobile. No abre puertos de red ni modifica el teléfono. Requiere que USB
  debugging esté habilitado y que el diálogo RSA se haya aceptado en el teléfono.
#>

$ErrorActionPreference = 'Stop'
$Base = Join-Path $env:LOCALAPPDATA 'ControlDroid'
$PlatformDir = Join-Path $Base 'platform-tools'
$ScrcpyDir = Join-Path $Base 'scrcpy'
New-Item -ItemType Directory -Force -Path $Base | Out-Null

function Get-Archive {
  param([Parameter(Mandatory)] [string] $Url, [Parameter(Mandatory)] [string] $Destination)
  Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
}

if (-not (Test-Path (Join-Path $PlatformDir 'adb.exe'))) {
  $platformZip = Join-Path $Base 'platform-tools.zip'
  Get-Archive -Url 'https://dl.google.com/android/repository/platform-tools-latest-windows.zip' -Destination $platformZip
  Expand-Archive -Path $platformZip -DestinationPath $Base -Force
  Remove-Item $platformZip -Force
}

if (-not (Test-Path (Join-Path $ScrcpyDir 'scrcpy.exe'))) {
  $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/Genymobile/scrcpy/releases/latest' -Headers @{ 'User-Agent' = 'ControlDroid-Bootstrap' }
  $asset = $release.assets | Where-Object { $_.name -match '^scrcpy-win64-.*\.zip$' } | Select-Object -First 1
  if (-not $asset) { throw 'No se encontró un archivo scrcpy para Windows de 64 bits.' }
  $scrcpyZip = Join-Path $Base $asset.name
  Get-Archive -Url $asset.browser_download_url -Destination $scrcpyZip
  $expanded = Join-Path $Base 'scrcpy-expanded'
  Remove-Item $expanded -Recurse -Force -ErrorAction SilentlyContinue
  Expand-Archive -Path $scrcpyZip -DestinationPath $expanded -Force
  $source = Get-ChildItem -Path $expanded -Directory | Select-Object -First 1
  if (-not $source) { throw 'No se pudo extraer scrcpy.' }
  Remove-Item $ScrcpyDir -Recurse -Force -ErrorAction SilentlyContinue
  Move-Item $source.FullName $ScrcpyDir
  Remove-Item $scrcpyZip, $expanded -Recurse -Force -ErrorAction SilentlyContinue
}

$env:Path = "$PlatformDir;$ScrcpyDir;$env:Path"
& (Join-Path $PlatformDir 'adb.exe') start-server | Out-Host
Write-Host "Dispositivos ADB detectados:" -ForegroundColor Cyan
& (Join-Path $PlatformDir 'adb.exe') devices -l | Out-Host

$devices = & (Join-Path $PlatformDir 'adb.exe') devices
if ($devices -match "`tdevice$") {
  Write-Host 'Abriendo scrcpy. Si el teléfono muestra una autorización RSA, acepta "Permitir siempre".' -ForegroundColor Green
  & (Join-Path $ScrcpyDir 'scrcpy.exe') --turn-screen-off --stay-awake
} else {
  Write-Warning 'No se detectó un teléfono autorizado. Revisa el cable USB, habilita Depuración USB y acepta la autorización RSA en el Honor X8; después ejecuta este script nuevamente.'
}
