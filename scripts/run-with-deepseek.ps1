param(
    [string]$KeyFile = "$HOME\Desktop\inherit\key.txt"
)

$ErrorActionPreference = "Stop"
if (-not $env:DEEPSEEK_API_KEY) {
    if (-not (Test-Path -LiteralPath $KeyFile)) {
        throw "DEEPSEEK_API_KEY is not set and key file was not found: $KeyFile"
    }
    $line = Get-Content -LiteralPath $KeyFile | Where-Object { $_ -match 'deepseek' } | Select-Object -First 1
    if (-not $line) {
        throw "No DeepSeek key entry was found in $KeyFile"
    }
    $value = if ($line -match '^[^:=]+[:=]\s*(.+)$') { $Matches[1].Trim() } else { $line.Trim() }
    if (-not $value) { throw "The DeepSeek key entry is empty" }
    $env:DEEPSEEK_API_KEY = $value
}

$env:SPRING_PROFILES_ACTIVE = "deepseek"
Push-Location (Join-Path $PSScriptRoot "..\music-backend")
try {
    & mvn spring-boot:run
    if ($LASTEXITCODE -ne 0) { throw "Backend exited with code $LASTEXITCODE" }
} finally {
    Pop-Location
}
