@echo off
chcp 65001 >nul
cd /d "%~dp0"
call gate_env\Scripts\activate.bat
python seed_virtual_data.py labels.csv --db "postgresql://postgres:1234@localhost:5432/scargo" --unregistered 0
pause
