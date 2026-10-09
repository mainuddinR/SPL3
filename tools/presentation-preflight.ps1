# Read-only presentation readiness check. No deployment, database write, or provider calls.
param([switch]$SkipInference)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root
$passed = 0
$failed = 0
function Result([string]$Name, [bool]$Ok, [string]$Details='') {
    if ($Ok) { $script:passed++; Write-Host "[PASS] $Name $Details" -ForegroundColor Green }
    else { $script:failed++; Write-Host "[FAIL] $Name $Details" -ForegroundColor Red }
}
function HttpCheck([string]$Name, [string]$Url) {
    try {
        $r = Invoke-WebRequest -UseBasicParsing -Uri $Url -TimeoutSec 8
        Result $Name ($r.StatusCode -eq 200) ("HTTP " + $r.StatusCode)
    } catch { Result $Name $false 'No HTTP 200 response' }
}
Write-Host 'Presentation preflight (no changes are made)' -ForegroundColor Cyan
try {
    docker info --format '{{.ServerVersion}}' 2>$null | Out-Null
    Result 'Docker engine' ($LASTEXITCODE -eq 0)
} catch { Result 'Docker engine' $false }
try {
    docker compose config --quiet 2>$null
    Result 'Compose configuration' ($LASTEXITCODE -eq 0)
} catch { Result 'Compose configuration' $false }
foreach($name in @('spl3-frontend-1','spl3-backend-1','spl3-ml-service-1')) {
    try {
        $status=(docker inspect -f '{{.State.Status}}' $name 2>$null).Trim()
        $health=(docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}not-defined{{end}}' $name 2>$null).Trim()
        $restart=(docker inspect -f '{{.HostConfig.RestartPolicy.Name}}' $name 2>$null).Trim()
        Result $name ($status -eq 'running' -and $health -ne 'unhealthy' -and $restart -eq 'unless-stopped') ("status=$status health=$health restart=$restart")
    } catch { Result $name $false 'Container missing or Docker unavailable' }
}
HttpCheck 'Angular UI' 'http://localhost:4200/'
HttpCheck 'Spring Boot + database health' 'http://localhost:8080/actuator/health'
HttpCheck 'FastAPI health (preloaded model)' 'http://localhost:8001/health'
try {
    docker compose exec -T backend sh -c 'getent hosts ml-service >/dev/null' 2>$null | Out-Null
    Result 'Backend -> ML DNS' ($LASTEXITCODE -eq 0)
} catch { Result 'Backend -> ML DNS' $false }
$model = Join-Path $root 'ml-service\models\codebert_satd\best_model\model.safetensors'
Result 'Local fine-tuned model file' ((Test-Path $model) -and (Get-Item $model -ErrorAction SilentlyContinue).Length -gt 100MB)
$compose = Get-Content (Join-Path $root 'compose.yml') -Raw
$oracle = [regex]::Match($compose, '(?m)^\s+ORACLE_HOST:\s*(\S+)\s*$').Groups[1].Value.Trim("' `"")
if($oracle -and $oracle -notmatch '\$') {
    try {
        $client = [System.Net.Sockets.TcpClient]::new()
        try { $connected = $client.ConnectAsync($oracle,1521).Wait(3000); Result 'Oracle listener reachable' $connected ("configured host=$oracle") }
        finally { $client.Dispose() }
    } catch { Result 'Oracle listener reachable' $false ("configured host=$oracle") }
} else { Result 'Oracle listener configuration' $false 'Configured host unresolved' }
if (-not $SkipInference) {
    try {
        $payload = @{comment='// TODO replace temporary hardcoded validation'; preceding_code='public boolean validate(String input) {'; succeeding_code='return input != null; }'} | ConvertTo-Json -Compress
        $output = Invoke-RestMethod -Method Post -Uri 'http://localhost:8001/api/v1/satd-detect' -Body $payload -ContentType 'application/json' -TimeoutSec 45
        Result 'Real CodeBERT inference' (($output.label -in @('SATD','NON-SATD')) -and ($null -ne $output.confidence)) ("label=$($output.label)")
    } catch { Result 'Real CodeBERT inference' $false 'Request did not return a valid prediction' }
}
Write-Host ''
Write-Host "Automated checks: $passed passed, $failed failed"
Write-Host 'Manual checks still required: GitHub re-login/session, one saved PR SATD analysis, one Design Check, notification refresh, optional LLM suggestion.'
if ($failed -gt 0) { exit 1 }
