$ErrorActionPreference = 'Stop'
$workspace = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$backend = Join-Path $workspace 'Valor-Backend'
$gateway = Join-Path $backend 'gateway'

foreach ($directory in @($backend, $gateway, (Join-Path $workspace 'Valor-Admin-Portal'), (Join-Path $workspace 'Valor-Android-APP'), (Join-Path $workspace 'Valor-technician'))) {
    if (-not (Test-Path $directory)) { throw "Missing repository or directory: $directory" }
}
$envFile = Join-Path $PSScriptRoot '.env'
if (-not (Test-Path $envFile)) { throw 'Create local-stack/.env with the required local database and JWT secrets first.' }
$localSecrets = @{}
foreach ($line in Get-Content $envFile) {
    if ($line -match '^\s*(MYSQL_ROOT_PASSWORD|VALOR_DB_PASSWORD|JWT_SECRET)=(.*)$') {
        $localSecrets[$matches[1]] = $matches[2].Trim()
    }
}
foreach ($key in @('MYSQL_ROOT_PASSWORD', 'VALOR_DB_PASSWORD', 'JWT_SECRET')) {
    if (-not $localSecrets[$key] -or $localSecrets[$key].StartsWith('replace-with-')) {
        throw "Set a local value for $key in local-stack/.env."
    }
}
if ($localSecrets['JWT_SECRET'].Length -lt 32) { throw 'JWT_SECRET must contain at least 32 characters.' }

$env:MAVEN_OPTS = '-Xmx512m'
$env:JAVA_TOOL_OPTIONS = '-Xmx768m -XX:ActiveProcessorCount=2'

Push-Location $backend
try {
    & mvn.cmd clean verify '-DreuseForks=false' '-DforkCount=1'
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
} finally { Pop-Location }

Push-Location $gateway
try {
    & mvn.cmd clean package
    if ($LASTEXITCODE -ne 0) { throw 'Gateway build failed.' }
} finally { Pop-Location }

& (Join-Path $PSScriptRoot 'build-local-web.ps1')
if (-not $?) { throw 'Web build failed.' }

& docker build -t valor-backend:local $backend
if ($LASTEXITCODE -ne 0) { throw 'Backend image build failed.' }

Push-Location $PSScriptRoot
try {
    & docker compose -f compose.yaml up -d --build
    if ($LASTEXITCODE -ne 0) { throw 'Docker Compose startup failed.' }
} finally { Pop-Location }

& (Join-Path $PSScriptRoot 'verify-local.ps1')
