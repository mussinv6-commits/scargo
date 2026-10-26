@echo off
chcp 65001 >nul
title S Group 전체 종료
echo S Group 서버를 종료합니다 : 백엔드 8080, OCR 8001, 웹 5173
for %%P in (8080 8001 5173) do (
  powershell -NoProfile -Command "Get-NetTCPConnection -LocalPort %%P -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
)
taskkill /FI "WINDOWTITLE eq S-Bot*" /T /F >nul 2>&1
echo 종료했습니다. PostgreSQL 은 그대로 둡니다.
timeout /t 3 >nul
