# Starts the backend with Maven lifecycle noise reduced.
# Runtime Spring Boot logs, warnings, and errors remain visible.

$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

& mvn -q spring-boot:run @args
exit $LASTEXITCODE
