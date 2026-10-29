param([string]$Repository = "FuzzsT/ENRROL_NEW")
$ErrorActionPreference = "Stop"
$Workflow = "build-aio-enrollment.yml"
if (-not (Get-Command gh -ErrorAction SilentlyContinue)) { throw "GitHub CLI (gh) is required." }
gh auth status | Out-Null
if ($LASTEXITCODE -ne 0) { throw "gh is not authenticated." }
gh workflow run $Workflow --repo $Repository -f signing_mode=emergency
if ($LASTEXITCODE -ne 0) { throw "Could not start emergency enrollment mode." }
Write-Host "Emergency enrollment mode started for $Repository"
$runId = gh run list --repo $Repository --workflow $Workflow --limit 1 --json databaseId --jq '.[0].databaseId'
if ($LASTEXITCODE -ne 0) { throw "Could not resolve workflow run." }
if ($runId) {
    Write-Host "Watching run $runId"
    gh run watch $runId --repo $Repository --exit-status
    if ($LASTEXITCODE -ne 0) { throw "Emergency enrollment workflow failed." }
    Write-Host "Release: https://github.com/$Repository/releases/tag/dpc-aio-emergency-enrollment"
}
