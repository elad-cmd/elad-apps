# Push med-knowledge-exam code to GitHub repo elad-cmd/Medical_Exam
# Run by double-clicking push-medical.bat
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot
$RepoUrl = "https://github.com/elad-cmd/Medical_Exam.git"

Write-Host "=== Push med-knowledge-exam to GitHub ===" -ForegroundColor Cyan

try { $g = git --version; Write-Host "OK  $g" -ForegroundColor Green }
catch { Write-Host "ERROR: git not installed. Get it from https://git-scm.com/download/win" -ForegroundColor Red; Read-Host "Press Enter to exit"; exit 1 }

if (-not (Test-Path ".git")) {
  Write-Host "-> Initializing local git repo..." -ForegroundColor Yellow
  git init | Out-Host
  git branch -M main | Out-Host
}

# .gitignore - keep junk files out
Set-Content -Encoding ASCII ".gitignore" "_sizetest.txt`r`n*.bak-*`r`n.fuse_hidden*"

Write-Host "-> Staging files..." -ForegroundColor Yellow
git add -A | Out-Host
git commit -m "Initial commit - med-knowledge-exam" 2>&1 | Out-Host

if (git remote 2>$null | Select-String -Quiet "origin") {
  git remote set-url origin $RepoUrl
} else {
  git remote add origin $RepoUrl
}

Write-Host "-> Pushing to GitHub (a sign-in window may pop up)..." -ForegroundColor Yellow
git push -u origin main 2>&1 | Out-Host
if ($LASTEXITCODE -ne 0) {
  Write-Host "ERROR: push failed. If a GitHub sign-in window appeared, sign in and run again." -ForegroundColor Red
  Read-Host "Press Enter to exit"; exit 1
}

Write-Host ""
Write-Host "DONE - code pushed to $RepoUrl" -ForegroundColor Green
Read-Host "Press Enter to close"
