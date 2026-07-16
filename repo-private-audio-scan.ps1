# Repo private audio/ZIP sızıntı taraması — Extension alanına göre (Include kullanmaz).
# Dönen yollar kök göreli ve / ile normalize edilir; mutlak kullanıcı yolu üretilmez.

function Find-RepoPrivateAudioLeaks {
    param(
        [Parameter(Mandatory = $true)][string]$Root
    )
    $allowedExt = @('.wav', '.mp3', '.zip')
    $excludedDirs = @('target', 'node_modules', '.git')
    if (-not (Test-Path -LiteralPath $Root)) {
        return @()
    }
    $rootFull = (Resolve-Path -LiteralPath $Root).Path
    $hits = New-Object System.Collections.Generic.List[string]
    Get-ChildItem -LiteralPath $rootFull -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
        if ($_.Extension.ToLowerInvariant() -notin $allowedExt) {
            return
        }
        $rel = $_.FullName.Substring($rootFull.Length).TrimStart('\', '/')
        $parts = @($rel -split '[\\/]')
        $underExcluded = $false
        if ($parts.Length -gt 1) {
            foreach ($seg in $parts[0..($parts.Length - 2)]) {
                if ($excludedDirs -contains $seg) {
                    $underExcluded = $true
                    break
                }
            }
        }
        if (-not $underExcluded) {
            [void]$hits.Add(($rel -replace '\\', '/'))
        }
    }
    return @($hits.ToArray())
}

function Format-RepoPrivateAudioLeakPublicOutput {
    param(
        [Parameter(Mandatory = $true)][AllowEmptyCollection()][string[]]$RelativePaths,
        [int]$MaxPaths = 10
    )
    $lines = New-Object System.Collections.Generic.List[string]
    [void]$lines.Add('PREFLIGHT_FAIL REPO_PRIVATE_AUDIO_LEAK')
    $shown = @($RelativePaths | Select-Object -First $MaxPaths)
    foreach ($rel in $shown) {
        if ([string]::IsNullOrWhiteSpace($rel)) { continue }
        # Yalnız göreli path; mutlak / user home yolu yazdırma
        $safe = ($rel -replace '\\', '/').TrimStart('/')
        if ($safe -match '^[A-Za-z]:' -or $safe.StartsWith('\\\\') -or $safe.Contains('Users/')) {
            continue
        }
        [void]$lines.Add("LEAK_PATH $safe")
    }
    return @($lines.ToArray())
}
