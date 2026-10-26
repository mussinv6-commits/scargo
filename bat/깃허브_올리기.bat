@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.." || goto :err
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set BR=%%b
echo 현재 브랜치: %BR%
rem 올리지 않을 것(_제외목록.txt)은 GitHub 추적에서만 해제. PC 파일은 그대로.
git rm -r -q --cached --ignore-unmatch --pathspec-from-file=_제외목록.txt >nul 2>&1
git add -A || goto :err
git commit -m "update: 불필요 파일 제외, README (%date%)"
git push -u origin %BR% || goto :err
echo.
echo [완료] https://github.com/mussinv6-commits/scargo/tree/%BR%
goto :end
:err
echo.
echo [오류] 위 메시지를 캡처해서 보내주세요.
:end
pause
