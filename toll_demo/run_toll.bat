@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist gate_env\Scripts\activate.bat (echo gate_env not found. Run setup_toll.bat first. & pause & exit /b 1)
call gate_env\Scripts\activate.bat

set GATE_DB_URL=postgresql://postgres:1234@localhost:5432/scargo
if exist db_url.txt set /p GATE_DB_URL=<db_url.txt

set GATE_YOLO=%~dp0models\yolo.pt
set GATE_OCR=%~dp0models\ocr.pth
set GATE_DTR_DIR=%~dp0dtr
set GATE_PIPELINE_DIR=%~dp0
set GATE_PHOTO_DIR=%~dp0photos
set TOLL_MIN_CONF=80
set GATE_WEB_PORT=8002

echo DB: %GATE_DB_URL%
echo Starting... (model loading takes 10-30 seconds)  Stop with Ctrl+C
echo The browser does NOT open automatically. Open http://localhost:8002 yourself, or use the SCARGO web menu.
python toll_web.py
pause