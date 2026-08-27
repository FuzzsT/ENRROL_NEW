param(
    [string]$Remote = "https://github.com/local-localhost-app-system/dpc_android.git",
    [string]$Branch = "main"
)
$ErrorActionPreference = "Stop"
$Root = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path
Set-Location $Root

if (-not (Test-Path ".git")) {
    git init
    if ($LASTEXITCODE -ne 0) { throw "git init failed" }
}

git checkout -B $Branch
if ($LASTEXITCODE -ne 0) { throw "git checkout failed" }

$origin = git remote get-url origin 2>$null
if ($LASTEXITCODE -eq 0 -and $origin) {
    git remote set-url origin $Remote
} else {
    git remote add origin $Remote
}
if ($LASTEXITCODE -ne 0) { throw "git remote configuration failed" }

$remoteLine = git ls-remote --heads origin "refs/heads/$Branch"
if ($LASTEXITCODE -ne 0) { throw "Cannot read remote branch. Authenticate Git/Git Credential Manager first." }
$oldSha = ""
if ($remoteLine) { $oldSha = ($remoteLine -split "\s+")[0] }

git add -A
if ($LASTEXITCODE -ne 0) { throw "git add failed" }
git diff --cached --quiet
if ($LASTEXITCODE -eq 0) {
    Write-Host "No changes to commit."
    exit 0
}

git -c user.name="DPC-AIO Migration" -c user.email="dpc-aio@users.noreply.github.com" commit -m "Migrate DPC-AIO 1.2.0 enrollment-ready source"
if ($LASTEXITCODE -ne 0) { throw "git commit failed" }

if ($oldSha) {
    Write-Host "Replacing $Branch with force-with-lease against $oldSha"
    git push origin "HEAD:$Branch" "--force-with-lease=$Branch`:$oldSha"
} else {
    Write-Host "Remote branch does not exist; creating $Branch"
    git push -u origin "HEAD:$Branch"
}
if ($LASTEXITCODE -ne 0) { throw "git push failed" }
