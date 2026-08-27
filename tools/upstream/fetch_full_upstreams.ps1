param([string]$Destination = "_upstream_downloads")
$ErrorActionPreference = "Stop"
$GoogleSha = "d42d7f196d2db3d22ba4fca1e74faa5bc9b58d4e"
$AdminSha = "2bc77ccd902f9b23de24fffbbf8336f22e502276"
$GoogleDir = Join-Path $Destination "google"
$AdminDir = Join-Path $Destination "admin-dpc"
New-Item -ItemType Directory -Force -Path $GoogleDir,$AdminDir | Out-Null
Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/googlesamples/android-testdpc/archive/$GoogleSha.zip" -OutFile (Join-Path $GoogleDir "android-testdpc-$GoogleSha.zip")
Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/ser-mk/admin-dpc/archive/$AdminSha.zip" -OutFile (Join-Path $AdminDir "admin-dpc-$AdminSha.zip")
Invoke-WebRequest -UseBasicParsing -Uri "https://github.com/ser-mk/admin-dpc/releases/download/v0.1/admin-dpc.apk" -OutFile (Join-Path $AdminDir "admin-dpc-v0.1.apk")
Get-ChildItem -File -Recurse $Destination | ForEach-Object {
  $h = Get-FileHash -Algorithm SHA256 $_.FullName
  "{0}  {1}" -f $h.Hash.ToLowerInvariant(), $_.FullName
} | Set-Content -Encoding ascii (Join-Path $Destination "SHA256SUMS.txt")
Write-Host "Upstream download complete: $Destination"
