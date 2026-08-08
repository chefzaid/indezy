$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendDirectory = Join-Path $repositoryRoot 'indezy-server'
$arguments = '/d /c "mvnw.cmd spring-boot:run > ..\indezy-server.log 2> ..\indezy-server-error.log"'

$process = Start-Process `
    -FilePath 'cmd.exe' `
    -ArgumentList $arguments `
    -WorkingDirectory $backendDirectory `
    -WindowStyle Hidden `
    -PassThru

Write-Host "Backend started with PID $($process.Id) (logs: indezy-server.log)"
