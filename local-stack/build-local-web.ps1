$ErrorActionPreference = 'Stop'
$workspace = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$env:VITE_API_BASE_URL = 'http://127.0.0.1:8080'
$env:EXPO_PUBLIC_API_BASE_URL = 'http://127.0.0.1:8080'
# Explicitly opt into HTTP only for the existing local preview guard.
$env:EXPO_PUBLIC_ALLOW_LOCAL_HTTP = 'true'
foreach ($app in @(
    @{ Directory = 'Valor-Admin-Portal'; Script = 'build' },
    @{ Directory = 'Valor-Android-APP'; Script = 'build:web' },
    @{ Directory = 'Valor-technician'; Script = 'export:web' }
)) {
    Push-Location (Join-Path $workspace $app.Directory)
    try {
        if (-not (Test-Path 'node_modules')) {
            & npm.cmd ci
            if ($LASTEXITCODE -ne 0) { throw "Dependency installation failed: $($app.Directory)" }
        }
        & npm.cmd run $app.Script
        if ($LASTEXITCODE -ne 0) { throw "Web build failed: $($app.Directory)" }
    } finally { Pop-Location }
}
