@echo off
chcp 65001 >nul
rem [1] 최신 코드 적용: AMR_적용.zip 을 최종파일에 덮어쓴다
cd /d "%~dp0.."
if not exist "%~dp0AMR_적용.zip" (echo AMR_적용.zip 이 없습니다 & pause & exit /b 1)
tar -xf "%~dp0AMR_적용.zip" -C "%CD%"
if errorlevel 1 (echo 압축 풀기 실패 & pause & exit /b 1)
echo.
echo 적용 완료. 다음:
echo  - STS 에서 F5 후 백엔드 재시작
echo  - 2_OCR서버_실행.bat
echo  - 웹 Ctrl+F5 / 앱은 app_build.bat 로 다시 빌드
pause
