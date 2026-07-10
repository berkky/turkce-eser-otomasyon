. (Join-Path $PSScriptRoot "konsol-utf8.ps1")
$ErrorActionPreference = "Stop"
$Maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { "C:\Tools\apache-maven-3.9.16\bin\mvn.cmd" }
if (-not (Test-Path $Maven)) { throw "Maven bulunamadi: $Maven" }

$steps = @(
    @{ Label = 'adim32derleme'; Type = 'compile' },
    @{ Label = 'adim21'; MainClass = 'Adim21Dogrulama' },
    @{ Label = 'adim23meta'; MainClass = 'Adim23Dogrulama' },
    @{ Label = 'adim24'; MainClass = 'Adim24Dogrulama' },
    @{ Label = 'adim25'; MainClass = 'Adim25Dogrulama' },
    @{ Label = 'adim26'; MainClass = 'Adim26Dogrulama'; Timeout = 180 },
    @{ Label = 'adim27'; MainClass = 'Adim27Dogrulama'; Timeout = 180 },
    @{ Label = 'adim28'; MainClass = 'Adim28Dogrulama'; Timeout = 240 },
    @{ Label = 'adim29'; MainClass = 'Adim29Dogrulama'; Timeout = 300 },
    @{ Label = 'adim30'; MainClass = 'Adim30Dogrulama'; Timeout = 300 },
    @{ Label = 'adim31'; MainClass = 'Adim31Dogrulama'; Timeout = 300 },
    @{ Label = 'adim32'; MainClass = 'Adim32Dogrulama'; Timeout = 300 }
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
        Write-TestBaslik 'adim32selftestFail'
        Write-Utf8Line ($step.Label + " - " + $_.Exception.Message)
        exit 1
    }
}

Write-Utf8Line ""
Write-TestBaslik 'adim32secret'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'check-secrets.ps1')
if ($LASTEXITCODE -ne 0) { throw "check-secrets.ps1 basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim32finalCheckTestMode'
& powershell -ExecutionPolicy Bypass -File (Join-Path $PSScriptRoot 'final-release-check.ps1') -TestMode
if ($LASTEXITCODE -ne 0) { throw "final-release-check.ps1 TestMode basarisiz" }

Write-Utf8Line ""
Write-TestBaslik 'adim32selftestOk'
exit 0
