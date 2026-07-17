param(
    [string]$KeyFile,
    [string]$Provider = $(if ($env:LLM_PROVIDER) { $env:LLM_PROVIDER } else { "deepseek" }),
    [string]$BaseUrl = $(if ($env:LLM_BASE_URL) { $env:LLM_BASE_URL } else { "https://api.deepseek.com" }),
    [string]$Model = $(if ($env:LLM_MODEL) { $env:LLM_MODEL } else { "deepseek-v4-flash" })
)

$ErrorActionPreference = "Stop"

if (-not $env:LLM_API_KEY) {
    if (-not $KeyFile) {
        throw "LLM_API_KEY is not set. Set it in the current terminal or pass -KeyFile."
    }
    if (-not (Test-Path -LiteralPath $KeyFile)) {
        throw "LLM key file was not found: $KeyFile"
    }
    $line = Get-Content -LiteralPath $KeyFile |
        Where-Object { $_ -match [regex]::Escape($Provider) } |
        Select-Object -First 1
    if (-not $line) {
        throw "No key entry matching provider '$Provider' was found in $KeyFile"
    }
    $value = if ($line -match '^[^:=]+[:=]\s*(.+)$') { $Matches[1].Trim() } else { $line.Trim() }
    if (-not $value) {
        throw "The configured LLM key entry is empty"
    }
    $env:LLM_API_KEY = $value
}

$env:LLM_PROVIDER = $Provider
$env:LLM_BASE_URL = $BaseUrl
$env:LLM_MODEL = $Model
$env:SPRING_PROFILES_ACTIVE = "llm"

Push-Location (Join-Path $PSScriptRoot "..\music-backend")
try {
    & .\mvnw.cmd spring-boot:run
    if ($LASTEXITCODE -ne 0) {
        throw "Backend exited with code $LASTEXITCODE"
    }
} finally {
    Pop-Location
}
