param(
    [string]$KeyFile = "$HOME\Desktop\inherit\key.txt"
)

# 向后兼容的 DeepSeek 快捷入口；通用模型配置请直接使用 run-with-llm.ps1。
& (Join-Path $PSScriptRoot "run-with-llm.ps1") `
    -KeyFile $KeyFile `
    -Provider "deepseek" `
    -BaseUrl "https://api.deepseek.com" `
    -Model "deepseek-v4-flash"

if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
