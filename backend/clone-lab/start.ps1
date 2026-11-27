[CmdletBinding()]
param(
    [ValidateRange(1024, 65535)][int]$Port = 8087,
    [switch]$CheckOnly
)

$ErrorActionPreference = 'Stop'
# Windows PowerShell 5.1 otherwise uses the system code page for native processes.
$labUtf8 = New-Object System.Text.UTF8Encoding($false)
[Console]::InputEncoding = $labUtf8
[Console]::OutputEncoding = $labUtf8
$OutputEncoding = $labUtf8
$backendPath = Split-Path -Parent $PSScriptRoot
$containerName = 'everme-clone-lab'
$databasePort = 55439
$labUrl = "http://127.0.0.1:$Port"

function Get-Listener([int]$Number) {
    Get-NetTCPConnection -LocalPort $Number -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
}

try {
    # Do not launch a second server or terminate an unrelated process.
    if (Get-Listener $Port) {
        try {
            $catalog = Invoke-RestMethod "$labUrl/api/lab/catalog" -TimeoutSec 5
        } catch { $catalog = $null }
        if ($catalog.version -eq 'lab-140-v1' -and $catalog.domains.Count -eq 7) {
            Write-Host "Clone Lab is already running: $labUrl/lab" -ForegroundColor Green
            Write-Host "Viewer: $labUrl/lab/viewer"
            exit 0
        }
        throw "Port $Port is in use by another process. Stop it, or run: .\clone-lab.cmd -Port 8088"
    }

    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker is not installed. Install and start Docker Desktop first.'
    }
    $null = & docker info --format '{{.ServerVersion}}' 2>$null
    if ($LASTEXITCODE -ne 0) { throw 'Docker is not ready. Start Docker Desktop and try again.' }

    if ($env:JAVA_HOME) {
        if (-not (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
            throw 'JAVA_HOME does not contain bin/java.exe. Configure a Java 21 JDK.'
        }
    } elseif (-not (Get-Command java -ErrorAction SilentlyContinue)) {
        throw 'Java 21 is required. Configure JAVA_HOME or add Java to PATH.'
    }
    if ([string]::IsNullOrWhiteSpace($env:OPENAI_API_KEY)) {
        throw 'OPENAI_API_KEY is not set. Set it in your terminal environment and run again.'
    }

    # This shortcut manages a local lab DB only; never use the production datasource.
    $expectedUrl = "jdbc:postgresql://localhost:$databasePort/everme_lab"
    if ($env:LAB_DATABASE_URL -and $env:LAB_DATABASE_URL -notin @($expectedUrl, "jdbc:postgresql://127.0.0.1:$databasePort/everme_lab")) {
        throw 'Custom LAB_DATABASE_URL detected. Use the manual README command for a custom database.'
    }
    foreach ($setting in @('LAB_DATABASE_USER', 'LAB_DATABASE_PASSWORD')) {
        $value = [Environment]::GetEnvironmentVariable($setting)
        if ($value -and $value -ne 'everme_lab') { throw "Custom $setting detected. Use the manual README command." }
    }

    $names = @(& docker container ls -a --format '{{.Names}}')
    if ($LASTEXITCODE -ne 0) { throw 'Unable to list Docker containers.' }
    $exists = $names -contains $containerName
    if ($exists) {
        $inspection = (& docker inspect $containerName | ConvertFrom-Json)[0]
        if ($LASTEXITCODE -ne 0) { throw 'Unable to inspect the lab container.' }
        $bindings = @($inspection.HostConfig.PortBindings.'5432/tcp')
        if ($inspection.Config.Image -notlike 'pgvector/pgvector:*' -or
            -not ($bindings | Where-Object { $_.HostPort -eq "$databasePort" -and $_.HostIp -eq '127.0.0.1' })) {
            throw "Existing $containerName has a different image or port mapping. It was not modified."
        }
    }

    if ($CheckOnly) {
        Write-Host 'Checks passed. No container or server was started.' -ForegroundColor Green
        Write-Host "DB: $(if ($exists) { 'reuse existing container' } else { 'create container and named volume' })"
        Write-Host "Server: $labUrl/lab (foreground; Ctrl+C to stop)"
        exit 0
    }

    if (-not $exists) {
        if (Get-Listener $databasePort) { throw "Database port $databasePort is occupied. No existing process was stopped." }
        & docker run --detach --name $containerName --publish "127.0.0.1:${databasePort}:5432" `
            --env POSTGRES_DB=everme_lab --env POSTGRES_USER=everme_lab --env POSTGRES_PASSWORD=everme_lab `
            --volume everme-clone-lab-data:/var/lib/postgresql/data pgvector/pgvector:pg17
        if ($LASTEXITCODE -ne 0) { throw 'Failed to create the lab database container.' }
    } elseif (-not $inspection.State.Running) {
        if (Get-Listener $databasePort) { throw "Database port $databasePort is occupied. Stop the conflicting container first." }
        & docker start $containerName
        if ($LASTEXITCODE -ne 0) { throw 'Failed to start the lab database container.' }
    }

    Write-Host 'Waiting for PostgreSQL...'
    $ready = $false
    for ($attempt = 0; $attempt -lt 60; $attempt++) {
        $null = & docker exec $containerName pg_isready -U everme_lab -d everme_lab 2>$null
        if ($LASTEXITCODE -eq 0) { $ready = $true; break }
        Start-Sleep -Seconds 1
    }
    if (-not $ready) { throw 'PostgreSQL did not become ready. Check docker logs everme-clone-lab.' }
    $null = & docker port $containerName '5432/tcp'
    if ($LASTEXITCODE -ne 0 -or -not (Get-Listener $databasePort)) {
        throw 'The DB container is running but its host port is unavailable. Check the container network configuration.'
    }

    Write-Host "Starting Clone Lab: $labUrl/lab" -ForegroundColor Green
    Write-Host "Viewer: $labUrl/lab/viewer"
    Write-Host 'Wait for Started BackendApplication. Gradle may stay at 80% while the server runs.'
    Write-Host 'Ctrl+C stops the server. The database and its data remain available.'
    # Pin the local connection in this child shell; preserve the caller's environment.
    $env:LAB_DATABASE_URL = $expectedUrl
    $env:LAB_DATABASE_USER = 'everme_lab'
    $env:LAB_DATABASE_PASSWORD = 'everme_lab'
    Push-Location $backendPath
    try {
        & .\gradlew.bat --console=plain bootRun "--args=--spring.profiles.active=clone-lab --server.port=$Port --lab.initialize-schema=true --server.address=127.0.0.1"
        $result = $LASTEXITCODE
    } finally { Pop-Location }
    exit $result
} catch {
    Write-Host "Clone Lab: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}
