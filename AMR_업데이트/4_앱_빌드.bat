@echo off
chcp 65001 >nul
rem [4] 앱 다시 빌드: 1_적용하기 로 바뀐 앱 코드를 APK 로 만든다 (amr-app\app_build.bat 실행)
call "%~dp0..\amr-app\app_build.bat"
