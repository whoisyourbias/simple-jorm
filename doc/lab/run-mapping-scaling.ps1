param([string]$OutputDirectory = "doc/lab/results/run-$(Get-Date -Format 'yyyyMMdd-HHmmss')")

$ErrorActionPreference = 'Stop'
foreach ($variableName in @('JORM_DB_URL', 'JORM_DB_USER', 'JORM_DB_PASSWORD')) {
    if ($null -eq [Environment]::GetEnvironmentVariable($variableName)) {
        throw "Set environment variable $variableName before running."
    }
}
$previousOutput = $env:JORM_BENCH_OUTPUT
$previousFork = $env:JORM_BENCH_FORK
try {
    $env:JORM_BENCH_OUTPUT = $OutputDirectory
    foreach ($forkNumber in 1..3) {
        $env:JORM_BENCH_FORK = [string]$forkNumber
        & .\gradlew.bat mappingScalingBenchmark --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Benchmark fork $forkNumber failed." }
        & .\gradlew.bat libraryMappingBenchmark --console=plain
        if ($LASTEXITCODE -ne 0) { throw "Library benchmark fork $forkNumber failed." }
    }
    python doc/lab/summarize_mapping_scaling.py $OutputDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Report generation failed.' }
} finally {
    $env:JORM_BENCH_OUTPUT = $previousOutput
    $env:JORM_BENCH_FORK = $previousFork
}
