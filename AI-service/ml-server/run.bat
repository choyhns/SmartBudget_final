@echo off
REM NumPy 1.x (venv) 사용 - ChromaDB 호환. 시스템 Python(C:\Python310) 사용 시 np.float_ 에러 발생.
set MYDIR=%~dp0
"%MYDIR%venv\Scripts\python.exe" "%MYDIR%run.py" %*
pause
