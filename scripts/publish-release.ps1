param(
    [Parameter(Mandatory = $true)]
    [string]$Tag,
    [Parameter(Mandatory = $true)]
    [string]$Title,
    [Parameter(Mandatory = $true)]
    [string]$Body,
    [Parameter(Mandatory = $true)]
    [string[]]$Assets
)

$ErrorActionPreference = "Stop"
$repo = "wxydejoy/Helm"

$credText = "protocol=https`nhost=github.com`n`n" | git credential fill 2>$null
$token = ($credText | Select-String '^password=(.+)$').Matches.Groups[1].Value
if (-not $token) { throw "无法从 git credential 获取 GitHub token" }

$headers = @{
    Authorization = "token $token"
    Accept        = "application/vnd.github+json"
    "X-GitHub-Api-Version" = "2022-11-28"
}

try {
    $existing = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/tags/$Tag" -Headers $headers -Method Get
} catch {
    $existing = $null
}
if ($existing -and $existing.id) {
    Write-Host "Release $Tag 已存在，删除后重建..."
    Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases/$($existing.id)" -Headers $headers -Method Delete | Out-Null
}

$payload = @{ tag_name = $Tag; name = $Title; body = $Body; draft = $false; prerelease = $false } | ConvertTo-Json
$release = Invoke-RestMethod -Uri "https://api.github.com/repos/$repo/releases" -Headers $headers -Method Post -Body $payload -ContentType "application/json; charset=utf-8"
Write-Host "Created release id=$($release.id)"

foreach ($asset in $Assets) {
    if (-not (Test-Path -LiteralPath $asset)) { throw "missing asset: $asset" }
    $name = [IO.Path]::GetFileName($asset)
    $uri = "https://uploads.github.com/repos/$repo/releases/$($release.id)/assets?name=$name"
    $bytes = [IO.File]::ReadAllBytes($asset)
    $uploadHeaders = @{
        Authorization = "token $token"
        Accept        = "application/vnd.github+json"
        "Content-Type" = "application/octet-stream"
    }
    Invoke-RestMethod -Uri $uri -Headers $uploadHeaders -Method Post -Body $bytes | Out-Null
    Write-Host "Uploaded $name"
}

Write-Host "OK: $($release.html_url)"
