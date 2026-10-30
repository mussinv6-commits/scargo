@echo off
chcp 65001 >nul
rem free ports used by old vite servers
powershell -NoProfile -Command "5173..5180 | ForEach-Object { Get-NetTCPConnection -LocalPort $_ -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue } }" >nul 2>&1
cd /d "%~dp0..\..\frontend"
if not exist node_modules call npm install
call npm run dev -- --strictPort
