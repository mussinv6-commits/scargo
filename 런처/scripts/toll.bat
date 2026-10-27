@echo off
chcp 65001 >nul
cd /d "%~dp0..\..\toll_demo"
call gate_env\Scripts\activate.bat
rem 시연 사진 번호의 차량/제원을 DB에 채움 (여러 번 해도 안전, 실패해도 서버는 계속 켬)
python seed_virtual_data.py labels.csv --db "postgresql://postgres:1234@localhost:5432/scargo" --unregistered 0 >nul 2>&1
call run_toll.bat
