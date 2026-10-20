@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0" || goto :err
for /f "delims=" %%b in ('git rev-parse --abbrev-ref HEAD') do set BR=%%b
echo 현재 브랜치: %BR%
rem 올리지 않을 것은 추적 해제 (PC 파일은 그대로, 이미 해제됐으면 무시)
git rm -r -q --cached --ignore-unmatch *.pptx 기술정리 메모 발표자료 회고_QA 영상 목업 화면_미리보기 _안쓰는파일 정리하기.bat 폴더정리.bat frontend/src/components/CSS/_색상변경전_백업 >nul 2>&1
git add -A || goto :err
git commit -m "update: S Group / S Cargo·S-Bot 분리, README, DB 정리 (%date%)"
git push -u origin %BR% || goto :err
echo.
echo [완료] https://github.com/mussinv6-commits/scargo/tree/%BR%
goto :end
:err
echo.
echo [오류] 위 메시지를 캡처해서 보내주세요.
:end
pause
