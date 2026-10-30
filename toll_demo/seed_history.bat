@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist gate_env\Scripts\activate.bat (echo gate_env not found. Run setup_toll.bat first. & pause & exit /b 1)
call gate_env\Scripts\activate.bat
set GATE_DB_URL=postgresql://postgres:1234@localhost:5432/scargo
if exist db_url.txt set /p GATE_DB_URL=<db_url.txt
echo DB: %GATE_DB_URL%
python seed_history.py --db "%GATE_DB_URL%"
pause
