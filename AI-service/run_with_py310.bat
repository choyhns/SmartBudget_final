@echo off
REM AI-service 통합 실행 (Python 3.10 고정)
REM 사용법: AI-service 폴더에서 run_with_py310.bat
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
set "VENV=%ROOT%.venv310"

py -3.10 -c "import sys; sys.exit(0 if sys.version_info[:2]==(3,10) else 1)" 2>nul
if errorlevel 1 (
    echo Python 3.10 not found. Install from https://www.python.org/downloads/release/python-3106/
    echo Check "Add Python to PATH" during install, then run this again.
    exit /b 1
)

if not exist "%VENV%\Scripts\python.exe" (
    echo Creating Python 3.10 venv: .venv310
    py -3.10 -m venv "%VENV%"
    if errorlevel 1 exit /b 1
)

REM UTF-8 인코딩 설정 (requirements.txt 한글 주석 처리용)
set "PYTHONIOENCODING=utf-8"
set "PYTHONUTF8=1"

echo Installing/updating dependencies...
"%VENV%\Scripts\pip.exe" install -q -r "%ROOT%requirements.txt"
if errorlevel 1 exit /b 1

echo Running run_all.py with Python 3.10...
cd /d "%ROOT%"
"%VENV%\Scripts\python.exe" "%ROOT%run_all.py" %*
