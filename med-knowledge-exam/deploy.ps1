# ============================================================
#  סקריפט העלאה — אתר מבחן הידע לרפואה
#  מעלה רק את קבצי האתר (HTML/CSS/JS/fonts) ל-GitHub.
#  משמיט: _ארכיון-ומחקר, גיבויים, מסמכים פנימיים (docx/md/pdf).
#  הפעלה (PowerShell):
#    powershell -ExecutionPolicy Bypass -File "C:\Users\elad\010_קלוד\claude_elad_michal\elad\apps\med-knowledge-exam\deploy.ps1"
# ============================================================

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# >>>>>>>>>>>>>>>>>>  מלא כאן את כתובת הריפו של האתר  <<<<<<<<<<<<<<<<<<
$RepoUrl       = "https://github.com/elad-cmd/SHEM-HA-REPO.git"
# <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

$Source        = "C:\Users\elad\010_קלוד\claude_elad_michal\elad\apps\med-knowledge-exam"
$CommitMessage = "עדכון: סיכומים מעמיקים, תרשימים ומנמוניקות, בנק 609 שאלות"

# מה להעלות (whitelist) — שאר הקבצים לא מועלים
$IncludeFiles = @("*.html","*.css","*.js")
$IncludeDirs  = @("fonts")

try { $v = git --version; Write-Host "git: $v" -ForegroundColor Green }
catch { Write-Host "git לא מותקן. התקן מ-https://git-scm.com" -ForegroundColor Red; Read-Host "Enter ליציאה"; exit 1 }

if ($RepoUrl -match "SHEM-HA-REPO") {
  Write-Host "✗ עדכן קודם את כתובת הריפו (RepoUrl) בראש הסקריפט." -ForegroundColor Red
  Read-Host "Enter ליציאה"; exit 1
}

$tmp = Join-Path $env:TEMP ("deploy_med_" + [Guid]::NewGuid().ToString('N').Substring(0,8))
Write-Host "→ משבט את הריפו..." -ForegroundColor Yellow
git clone --depth 1 $RepoUrl $tmp
if ($LASTEXITCODE -ne 0) { Write-Host "✗ שגיאה בשיבוט — בדוק כתובת ריפו וחיבור ל-GitHub" -ForegroundColor Red; Read-Host "Enter"; exit 1 }

Write-Host "→ מנקה תוכן ישן..." -ForegroundColor Yellow
Get-ChildItem -Path $tmp -Force | Where-Object { $_.Name -ne ".git" } | Remove-Item -Recurse -Force

Write-Host "→ מעתיק את קבצי האתר בלבד..." -ForegroundColor Yellow
foreach ($pat in $IncludeFiles) {
  Get-ChildItem -Path $Source -Filter $pat -File | Copy-Item -Destination $tmp -Force
}
foreach ($d in $IncludeDirs) {
  if (Test-Path (Join-Path $Source $d)) { Copy-Item -Path (Join-Path $Source $d) -Destination $tmp -Recurse -Force }
}

Push-Location $tmp
try {
  git add -A
  if (-not (git status --porcelain)) {
    Write-Host "→ אין שינויים להעלות (הריפו מסונכרן)" -ForegroundColor Yellow
  } else {
    git commit -m $CommitMessage | Out-Host
    Write-Host "→ דוחף ל-GitHub..." -ForegroundColor Yellow
    git push origin main | Out-Host
    if ($LASTEXITCODE -eq 0) { Write-Host "✓ הועלה בהצלחה! הפריסה תושלם תוך כדקה." -ForegroundColor Green }
    else { Write-Host "✗ שגיאה בדחיפה" -ForegroundColor Red }
  }
} finally {
  Pop-Location
  Remove-Item -Recurse -Force $tmp -ErrorAction SilentlyContinue
}
Read-Host "Enter ליציאה"
