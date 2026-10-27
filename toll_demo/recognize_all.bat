@echo off
chcp 65001 >nul
cd /d "%~dp0"
call gate_env\Scripts\activate.bat
set GATE_YOLO=%~dp0models\yolo.pt
set GATE_OCR=%~dp0models\ocr.pth
set GATE_DTR_DIR=%~dp0dtr
set GATE_PIPELINE_DIR=%~dp0
python recognize_all.py photos
pause
