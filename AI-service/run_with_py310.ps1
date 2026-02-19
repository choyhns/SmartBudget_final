# AI-service 통합 실행 (Python 3.10 고정)
# 사용법: AI-service 폴더에서 .\run_with_py310.ps1
# 필요: Python 3.10.x 설치 (https://www.python.org/downloads/release/python-3106/)

# PowerShell 출력 인코딩을 UTF-8로 설정 (한글 깨짐 방지)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding = [System.Text.Encoding]::UTF8
chcp 65001 | Out-Null

$ErrorActionPreference = "Stop"
$ProjectRoot = $PSScriptRoot
$VenvDir = Join-Path $ProjectRoot ".venv310"

function Get-Py310Path {
    # Windows Python Launcher (py -3.10)
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) {
        $ver = & py -3.10 -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null
        if ($LASTEXITCODE -eq 0 -and $ver -match "^3\.10") { return "py -3.10" }
    }
    # PATH의 python3.10
    $p310 = Get-Command python3.10 -ErrorAction SilentlyContinue
    if ($p310) { return "python3.10" }
    # pyenv 등
    $p = Get-Command python -ErrorAction SilentlyContinue
    if ($p) {
        $v = & python -c "import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}')" 2>$null
        if ($v -eq "3.10") { return "python" }
    }
    return $null
}

$py310 = Get-Py310Path
if (-not $py310) {
    Write-Host "Python 3.10을 찾을 수 없습니다." -ForegroundColor Red
    Write-Host "1) https://www.python.org/downloads/release/python-3106/ 에서 Windows installer 다운로드" -ForegroundColor Yellow
    Write-Host "2) 설치 시 'Add Python to PATH' 체크 후 설치" -ForegroundColor Yellow
    Write-Host "3) 설치 후 터미널을 다시 열고 이 스크립트를 다시 실행하세요." -ForegroundColor Yellow
    exit 1
}

# 가상환경 없으면 생성
if (-not (Test-Path (Join-Path $VenvDir "Scripts\python.exe"))) {
    Write-Host "Python 3.10 가상환경 생성 중: .venv310" -ForegroundColor Cyan
    if ($py310 -eq "py -3.10") {
        py -3.10 -m venv $VenvDir
    } else {
        Invoke-Expression "$py310 -m venv $VenvDir"
    }
    if ($LASTEXITCODE -ne 0) { exit 1 }
}

$venvPython = Join-Path $VenvDir "Scripts\python.exe"
$venvPip = Join-Path $VenvDir "Scripts\pip.exe"

# UTF-8 인코딩 설정 (requirements.txt 한글 주석 처리용)
$env:PYTHONIOENCODING = "utf-8"
$env:PYTHONUTF8 = "1"

# 의존성 설치 (최초 1회 또는 requirements 변경 시)
Write-Host "의존성 확인 중..." -ForegroundColor Cyan
& $venvPip install -q -r (Join-Path $ProjectRoot "requirements.txt")
if ($LASTEXITCODE -ne 0) {
    Write-Host "pip install 실패. 위 오류를 확인하세요." -ForegroundColor Red
    exit 1
}

# run_all.py 실행
Write-Host "run_all.py 실행 (Python 3.10)..." -ForegroundColor Green
Set-Location $ProjectRoot
& $venvPython (Join-Path $ProjectRoot "run_all.py") @args
