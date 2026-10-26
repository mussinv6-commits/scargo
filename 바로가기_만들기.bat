@echo off
chcp 65001 >nul
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0바로가기_만들기.ps1"
echo.
echo 바탕화면에 [S그룹 실행] [S그룹 종료] 바로가기가 만들어졌습니다.
pause
