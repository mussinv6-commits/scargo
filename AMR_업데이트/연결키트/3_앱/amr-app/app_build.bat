@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo [1/4] npm install
call npm install || goto :err
echo [2/4] web build
call npm run build || goto :err
if not exist android (
  echo [3/4] create android project
  call npx cap add android || goto :err
  echo android.overridePathCheck=true>> android\gradle.properties
) else (
  echo [3/4] android project exists
)
findstr /c:"android.overridePathCheck" android\gradle.properties > nul || echo android.overridePathCheck=true>> android\gradle.properties
echo [4/4] sync and open Android Studio
call npx cap sync android || goto :err
call npx cap open android
echo.
echo DONE. In Android Studio: Build - Generate App Bundles or APKs - Generate APKs
echo APK: android\app\build\outputs\apk\debug\app-debug.apk
pause
exit /b 0
:err
echo.
echo ERROR - see the message above
pause
exit /b 1
