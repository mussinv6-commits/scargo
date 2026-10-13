@echo off
cd /d "%~dp0"

set PYW=C:\Users\user\anaconda3\pythonw.exe

if exist "%PYW%" (
    start "" "%PYW%" plate_detector_gui.py
    goto :eof
)

where pythonw >nul 2>nul
if %errorlevel%==0 (
    start "" pythonw plate_detector_gui.py
    goto :eof
)

echo Could not find pythonw.exe automatically.
echo Please open Anaconda Prompt and run:
echo   cd /d "%~dp0"
echo   python plate_detector_gui.py
pause
