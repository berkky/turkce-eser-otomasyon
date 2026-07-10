. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

$steps = @(
    @{ Label = 'adim31derleme'; Type = 'compile' },
    @{ Label = 'adim21'; MainClass = 'Adim21Dogrulama' },
    @{ Label = 'adim23meta'; MainClass = 'Adim23Dogrulama' },
    @{ Label = 'adim24'; MainClass = 'Adim24Dogrulama' },
    @{ Label = 'adim25'; MainClass = 'Adim25Dogrulama' },
    @{ Label = 'adim26'; MainClass = 'Adim26Dogrulama'; Timeout = 180 },
    @{ Label = 'adim27'; MainClass = 'Adim27Dogrulama'; Timeout = 180 },
    @{ Label = 'adim28'; MainClass = 'Adim28Dogrulama'; Timeout = 240 },
    @{ Label = 'adim29'; MainClass = 'Adim29Dogrulama'; Timeout = 300 },
    @{ Label = 'adim30'; MainClass = 'Adim30Dogrulama'; Timeout = 300 },
    @{ Label = 'adim31'; MainClass = 'Adim31Dogrulama'; Timeout = 300 }
)

foreach ($step in $steps) {
    Write-Utf8Line ""
    try {
        if ($step.Type -eq 'compile') {
            Write-TestBaslik $step.Label
            & $Maven -q -DskipTests compile
            if ($LASTEXITCODE -ne 0) { throw "Derleme basarisiz (exit $LASTEXITCODE)" }
        } else {
            $timeout = if ($step.Timeout) { $step.Timeout } else { 300 }
            Invoke-MavenExecTimeout -Maven $Maven -MainClass $step.MainClass -StepLabel $step.Label -TimeoutSeconds $timeout
        }
    } catch {
        Write-Utf8Line ""
        Write-TestBaslik 'adim31selftestFail'
        Write-Utf8Line ($step.Label + " - " + $_.Exception.Message)
        exit 1
    }
}

Write-Utf8Line ""
Write-TestBaslik 'adim31plan5'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-plan.ps1') -EserId 5
if ($LASTEXITCODE -ne 0) { throw "tam-eser-plan ESER-5 basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim31plan6'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-plan.ps1') -EserId 6
if ($LASTEXITCODE -ne 0) { throw "tam-eser-plan ESER-6 basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim31onay5'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-onay-taslagi.ps1') -EserId 5
if ($LASTEXITCODE -ne 0) { throw "tam-eser-onay-taslagi basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim31kuyruk5'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'tam-eser-kuyruga-al.ps1') -EserId 5 -Onayli
if ($LASTEXITCODE -ne 0) { throw "tam-eser-kuyruga-al basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim31selftestOk'
exit 0
