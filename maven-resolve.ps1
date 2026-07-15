function Resolve-MavenCommand {
    [CmdletBinding()]
    param()
    if (-not [string]::IsNullOrWhiteSpace($env:MAVEN_CMD) -and (Test-Path -LiteralPath $env:MAVEN_CMD)) {
        return (Resolve-Path -LiteralPath $env:MAVEN_CMD).Path
    }
    $cmd = Get-Command mvn.cmd -ErrorAction SilentlyContinue
    if ($null -ne $cmd -and $cmd.Source) { return $cmd.Source }
    $cmd = Get-Command mvn -ErrorAction SilentlyContinue
    if ($null -ne $cmd -and $cmd.Source) { return $cmd.Source }
    $fallback = Join-Path $env:USERPROFILE "tools\apache-maven-3.9.16\bin\mvn.cmd"
    if (Test-Path -LiteralPath $fallback) {
        return (Resolve-Path -LiteralPath $fallback).Path
    }
    throw "MAVEN_NOT_FOUND"
}
