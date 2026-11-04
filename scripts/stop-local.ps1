# Stops the services started by start-local.ps1
$root = Split-Path -Parent $PSScriptRoot
$pids = Join-Path $root 'logs\pids.txt'
if (-not (Test-Path $pids)) { Write-Host 'Nothing to stop (logs\pids.txt not found).'; exit 0 }
Get-Content $pids | ForEach-Object {
    try { Stop-Process -Id ([int]$_) -Force -ErrorAction Stop; Write-Host "stopped pid $_" } catch { }
}
Remove-Item $pids
