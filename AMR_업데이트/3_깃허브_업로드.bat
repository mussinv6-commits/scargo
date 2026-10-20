@echo off
chcp 65001 >nul
setlocal
rem [3] 깃허브 feature/amr-link 브랜치에 올리기 (main 과 작업 중인 파일은 안 바뀜)
cd /d "%~dp0.." || goto :err
git fetch origin feature/amr-link || goto :err
git fetch "%~dp0amr_link.bundle" feature/amr-link:feature/amr-link || goto :err
git push origin feature/amr-link || goto :err
echo.
echo [완료] https://github.com/mussinv6-commits/scargo/tree/feature/amr-link
goto :end
:err
echo.
echo [오류] 위 메시지를 캡처해서 보내주세요.
:end
pause
