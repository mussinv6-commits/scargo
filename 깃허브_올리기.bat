@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0" || goto :err
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set BR=%%b
echo 현재 브랜치: %BR%
git add -A || goto :err
git commit -m "feat: S Group 메인/로그인, S Cargo·S-Bot 분리, 컬럼 불일치 수정"
git push origin %BR% || goto :err
echo.
echo [완료] https://github.com/mussinv6-commits/scargo/tree/%BR%
goto :end
:err
echo.
echo [오류] 위 메시지를 캡처해서 보내주세요.
:end
pause
