@echo off
chcp 65001 >nul
rem Build amr-app debug APK and put it in apk\ (old APKs go to _unused\apk_old). ASCII only.
setlocal
set "ROOT=%~dp0..\"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd"') do set "D=%%i"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
if not defined JAVA_HOME echo [ERROR] JAVA_HOME not set and Android Studio jbr not found. & pause & exit /b 1

cd /d "%ROOT%amr-app"
echo [1/4] npm install
call npm install || goto :fail
echo [2/4] vite build + cap sync
call npm run sync || goto :fail
echo [3/4] gradle assembleDebug
cd android
call gradlew.bat assembleDebug || goto :fail
cd ..
set "APK=android\app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" goto :fail

echo [4/4] copy to apk\
if not exist "%ROOT%apk" mkdir "%ROOT%apk"
if not exist "%ROOT%_안쓰는파일\apk_old" mkdir "%ROOT%_안쓰는파일\apk_old"
move /y "%ROOT%apk\sbot-amr-app_*.apk" "%ROOT%_안쓰는파일\apk_old\" >nul 2>nul
copy /y "%APK%" "%ROOT%apk\sbot-amr-app_%D%.apk" >nul
echo.
echo [OK] %ROOT%apk\sbot-amr-app_%D%.apk
echo Next: run 공유용_만들기.bat to include it in the share package.
explorer /select,"%ROOT%apk\sbot-amr-app_%D%.apk"
pause
exit /b 0

:fail
echo.
echo [FAIL] build failed - check the messages above.
pause
exit /b 1
