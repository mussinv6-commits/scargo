@echo off
chcp 65001 > nul
rem Upload amr-app folder to the team repo as branch feature/amr-app
cd /d "%~dp0.."
if exist amr-app\.git (
  echo Removing separate repo info amr-app\.git ^(created by old script^)
  rmdir /s /q amr-app\.git
)
for /f %%b in ('git branch --show-current') do set CUR=%%b
echo Current branch: %CUR%
if /i "%CUR%"=="feature/amr-app" goto :add
git switch -c feature/amr-app 2>nul || git switch feature/amr-app || goto :err
:add
git add amr-app || goto :err
git commit -m "Add S-Cargo AMR control app (amr-app)"
git push -u origin feature/amr-app || goto :err
echo.
echo DONE - pushed to branch feature/amr-app
echo You are now on branch feature/amr-app
pause
exit /b 0
:err
echo.
echo ERROR - see the message above
pause
exit /b 1
