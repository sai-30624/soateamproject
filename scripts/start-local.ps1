<#
  Starts all PayVault back-end services in the background (Windows PowerShell).

  Usage (from the project root):
    .\scripts\start-local.ps1            # uses H2 file databases - no MySQL needed
    .\scripts\start-local.ps1 -MySql     # uses MySQL on localhost:3306 (set $env:DB_USER / $env:DB_PASS first)
    .\scripts\stop-local.ps1             # stops everything

  Build first:  .\mvnw.cmd clean package -DskipTests
  Logs go to .\logs\<service>.log
#>
param([switch]$MySql)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
New-Item -ItemType Directory -Force logs | Out-Null

$profileArg = if ($MySql) { @() } else { @('--spring.profiles.active=h2') }

function Start-Service($name, [string[]]$extra) {
    $jar = Join-Path $root "$name\target\$name.jar"
    if (-not (Test-Path $jar)) { throw "$jar not found - run .\mvnw.cmd clean package -DskipTests first" }
    $p = Start-Process -FilePath 'java' -ArgumentList (@('-jar', $jar) + $extra) `
        -RedirectStandardOutput "logs\$name.log" -RedirectStandardError "logs\$name.err.log" `
        -WindowStyle Hidden -PassThru
    Add-Content logs\pids.txt $p.Id
    Write-Host "started $name (pid $($p.Id))"
}

function Wait-Port($port, $name) {
    for ($i = 0; $i -lt 90; $i++) {
        try { $c = New-Object Net.Sockets.TcpClient('localhost', $port); $c.Close(); Write-Host "  $name is up on :$port"; return } catch { Start-Sleep 1 }
    }
    throw "$name did not open port $port - see logs\$name.log"
}

Remove-Item logs\pids.txt -ErrorAction SilentlyContinue
Start-Service 'eureka-server' @()
Wait-Port 8761 'eureka-server'
foreach ($s in 'auth-service', 'user-service', 'wallet-service', 'transaction-service') { Start-Service $s $profileArg }
Start-Service 'api-gateway' @()
Wait-Port 8081 'auth-service'; Wait-Port 8082 'user-service'; Wait-Port 8083 'wallet-service'
Wait-Port 8084 'transaction-service'; Wait-Port 8080 'api-gateway'

Write-Host ""
Write-Host "All services started. Give Eureka ~30 s to register everything (http://localhost:8761)."
Write-Host "Then: cd payvault-ui; npm install; npm run dev   ->  http://localhost:5173"
Write-Host "Smoke test: node scripts\smoke-test.mjs"
