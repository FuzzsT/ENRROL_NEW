param([string]$Repository = "FuzzsT/ENRROL_NEW")
$ErrorActionPreference = "Stop"
$Workflow = "build-aio-enrollment.yml"

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI (gh) is required." }
gh auth status | Out-Null
if ($LASTEXITCODE -ne 0) { throw "gh is not authenticated." }

Write-Host "Starting NORMAL/STABLE enrollment build for $Repository"
Write-Host "This launcher never falls back to emergency signing."

gh workflow run $Workflow --repo $Repository -f signing_mode=stable
if ($LASTEXITCODE -ne 0) { throw "Could not start stable enrollment mode." }

Start-Sleep -Seconds 2
$runId = gh run list --repo $Repository --workflow $Workflow --event workflow_dispatch --limit 1 --json databaseId,status,conclusion --jq '.[0].databaseId'
if ($LASTEXITCODE -ne 0 -or -not $runId) { throw "Could not resolve stable workflow run." }

Write-Host "Watching stable run $runId"
gh run watch $runId --repo $Repository --exit-status
if ($LASTEXITCODE -ne 0) {
    Write-Host "Stable build failed. Check repository signing secrets/expected certificate; emergency is intentionally NOT used as fallback." -ForegroundColor Red
    throw "Stable enrollment workflow failed."
}

Write-Host "Stable release: https://github.com/$Repository/releases/tag/dpc-aio-continuous"
