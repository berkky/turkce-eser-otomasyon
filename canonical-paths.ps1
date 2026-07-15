function Get-EserCanonicalPaths {
    [CmdletBinding()]
    param(
        [string]$UserProfile = $env:USERPROFILE,
        [hashtable]$EnvironmentOverrides
    )

    if ([string]::IsNullOrWhiteSpace($UserProfile)) {
        $UserProfile = [Environment]::GetFolderPath('UserProfile')
    }
    $desktop = Join-Path ([System.IO.Path]::GetFullPath($UserProfile)) 'Desktop'
    $canonicalRoot = Join-Path $desktop 'ESER'
    $warnings = [System.Collections.Generic.List[string]]::new()

    function Resolve-EserPath {
        param([string]$Name, [string]$DefaultPath, [bool]$Directory)
        $value = if ($null -ne $EnvironmentOverrides) {
            $EnvironmentOverrides[$Name]
        } else {
            [Environment]::GetEnvironmentVariable($Name, 'Process')
        }
        if ([string]::IsNullOrWhiteSpace($value)) { return $DefaultPath }
        try {
            if (-not [System.IO.Path]::IsPathRooted($value)) {
                $warnings.Add("INVALID_PATH_OVERRIDE:$Name") | Out-Null
                return $DefaultPath
            }
            $path = [System.IO.Path]::GetFullPath($value)
            if (Test-Path -LiteralPath $path) {
                $item = Get-Item -LiteralPath $path
                if (($Directory -and -not $item.PSIsContainer) -or (-not $Directory -and $item.PSIsContainer)) {
                    $warnings.Add("INVALID_PATH_OVERRIDE:$Name") | Out-Null
                    return $DefaultPath
                }
            }
            return $path
        } catch {
            $warnings.Add("INVALID_PATH_OVERRIDE:$Name") | Out-Null
            return $DefaultPath
        }
    }

    $legacyRoots = @(
        (Join-Path $desktop 'eser-otomasyon-kuyruk'),
        (Join-Path $desktop 'metin-arsivi'),
        (Join-Path $desktop 'ses-arsivi'),
        (Join-Path $desktop 'arsiv')
    )
    $legacyDetected = @($legacyRoots | Where-Object { Test-Path -LiteralPath $_ }).Count -gt 0
    if ($legacyDetected) { $warnings.Add('LEGACY_DATA_ROOT_DETECTED') | Out-Null }

    [PSCustomObject]@{
        CanonicalRoot = $canonicalRoot
        LegacyRoot = $desktop
        Gelen = Resolve-EserPath 'ESER_GELEN_KLASORU' (Join-Path $canonicalRoot 'gelen-eser') $true
        Arsiv = Resolve-EserPath 'ESER_ARSIVI' (Join-Path $canonicalRoot 'arsiv') $true
        Metin = Resolve-EserPath 'ESER_METIN_ARSIVI' (Join-Path $canonicalRoot 'metin-arsivi') $true
        Ses = Resolve-EserPath 'ESER_SES_ARSIVI' (Join-Path $canonicalRoot 'ses-arsivi') $true
        Kuyruk = Resolve-EserPath 'ESER_URETIM_KUYRUGU' (Join-Path $canonicalRoot 'eser-otomasyon-kuyruk') $true
        Katalog = Resolve-EserPath 'ESER_KATALOGU' (Join-Path $canonicalRoot 'eser-katalogu.xlsx') $false
        KalitePanel = Resolve-EserPath 'SES_KALITE_PANEL' (Join-Path $canonicalRoot 'ses-arsivi_kalite-panel') $true
        ProductionApprovals = Join-Path (Resolve-EserPath 'ESER_URETIM_KUYRUGU' `
            (Join-Path $canonicalRoot 'eser-otomasyon-kuyruk') $true) 'production-approvals'
        LegacyPaths = $legacyRoots
        LegacyDetected = $legacyDetected
        Warnings = @($warnings)
    }
}
