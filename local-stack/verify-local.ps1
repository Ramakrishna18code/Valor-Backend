$ErrorActionPreference = 'Stop'

$urls = @(
    'http://127.0.0.1:8081/api/v1/health',
    'http://127.0.0.1:8080/api/v1/health',
    'http://127.0.0.1:3000',
    'http://127.0.0.1:3001',
    'http://127.0.0.1:3002'
)
foreach ($url in $urls) {
    $healthy = $false
    for ($attempt = 0; $attempt -lt 30; $attempt++) {
        try {
            $response = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -eq 200) { $healthy = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $healthy) { throw "Local endpoint did not become healthy: $url" }
    Write-Host "Ready: $url"
}

foreach ($port in @(3000, 3001, 3002)) {
    $page = Invoke-WebRequest "http://127.0.0.1:$port/" -UseBasicParsing -TimeoutSec 8
    $script = [regex]::Match($page.Content, 'src="([^"]+\.js)"')
    if (-not $script.Success) { throw "App $port has no JavaScript bundle." }
    $bundle = Invoke-WebRequest "http://127.0.0.1:$port$($script.Groups[1].Value)" -UseBasicParsing -TimeoutSec 20
    if ($bundle.StatusCode -ne 200 -or -not $bundle.Content.Contains('8080')) {
        throw "App $port is not serving its gateway-configured bundle."
    }
}

foreach ($port in @(3000, 3001, 3002)) {
    $origin = "http://127.0.0.1:$port"
    $preflight = Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/auth/login' -Method Options -Headers @{
        Origin = $origin
        'Access-Control-Request-Method' = 'POST'
        'Access-Control-Request-Headers' = 'authorization,content-type'
    } -UseBasicParsing -TimeoutSec 8
    if ($preflight.StatusCode -ne 200 -or $preflight.Headers['Access-Control-Allow-Origin'] -ne $origin) {
        throw "Gateway CORS preflight failed for $origin."
    }
}

try {
    Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/health' -Method Options -Headers @{
        Origin = 'https://untrusted.example'
        'Access-Control-Request-Method' = 'GET'
    } -UseBasicParsing -TimeoutSec 8 -ErrorAction Stop | Out-Null
    throw 'Gateway accepted an untrusted browser origin.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 403) { throw }
}

try {
    Invoke-WebRequest 'http://127.0.0.1:8080/api/v1/me' -UseBasicParsing -TimeoutSec 8 -ErrorAction Stop | Out-Null
    throw 'Protected API accepted an anonymous request.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
}

$socket = [System.Net.WebSockets.ClientWebSocket]::new()
$timeout = [System.Threading.CancellationTokenSource]::new(8000)
try {
    $socket.ConnectAsync([Uri]'ws://127.0.0.1:8080/ws', $timeout.Token).GetAwaiter().GetResult() | Out-Null
    if ($socket.State -ne [System.Net.WebSockets.WebSocketState]::Open) { throw 'Gateway WebSocket did not open.' }
} finally {
    $socket.Dispose()
    $timeout.Dispose()
}

Write-Host 'Local VALOR stack verification passed.'
