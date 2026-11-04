@echo off
setlocal enabledelayedexpansion
rem Patch 1103 (port gate exit -> section control, overload list split). ASCII only. Safe to re-run.
rem Usage: apply_patch_1103.bat [project folder]   (project folder = the folder that has backend, frontend, toll_demo)
rem Every file is backed up to <project>\bat\backup_patch_1103_<time> before it is overwritten.
rem No DB script: toll_web.py adds departure_logs.gate_log_id by itself on start (IF NOT EXISTS).
set "SRC=%~dp0files"
set "LOG=%~dp0apply_patch_1103.log"
set "TARGET=%~1"
if "%TARGET%"=="" if exist "%~dp0..\backend\scargo\src\main\java" set "TARGET=%~dp0.."
if "%TARGET%"=="" (
  echo Project folder = the folder that has backend, frontend, toll_demo
  set /p "TARGET=Drag the project folder here and press Enter: "
)
if "%TARGET%"=="" goto bad_target
set "TARGET=%TARGET:"=%"
if "%TARGET:~-1%"=="\" set "TARGET=%TARGET:~0,-1%"
if not exist "%TARGET%\backend\scargo\src\main\java\com\scargo" goto bad_target
if not exist "%TARGET%\toll_demo\toll_web.py" goto bad_target
if not exist "%SRC%\backend" goto bad_src

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%t"
set "BK=%TARGET%\bat\backup_patch_1103_%TS%"
mkdir "%BK%" >nul 2>nul
echo ==== %date% %time% ==== > "%LOG%"
echo target: %TARGET% >> "%LOG%"
echo backup: %BK% >> "%LOG%"

echo Copying files (backup: bat\backup_patch_1103_%TS%)
set FAILED=0
set COUNT=0
for %%p in (
  backend\scargo\src\main\java\com\scargo\controller\OverloadController.java
  backend\scargo\src\main\java\com\scargo\dto\OverloadCheckResponse.java
  backend\scargo\src\main\java\com\scargo\entity\OverloadCheck.java
  backend\scargo\src\main\java\com\scargo\repository\OverloadCheckRepository.java
  backend\scargo\src\main\java\com\scargo\service\OverloadService.java
  frontend\src\components\admin\adminMenu.js
  frontend\src\components\admin\screens\AdminOverloadChecks.vue
  frontend\src\components\gate\GateDemo.vue
  toll_demo\common.js
  toll_demo\toll_db.py
  toll_demo\toll_web.py
) do call :put "%%p"
copy /y "%~dp0restore_patch_1103.bat" "%BK%\" >nul
echo   !COUNT! files copied
echo.
if "%FAILED%"=="1" ( echo Some files failed. Log: !LOG! ) else ( echo [OK] Patch applied. )
echo Next:
echo   1. Restart the backend (launcher recompiles changed sources)
echo   2. Close the run_toll.bat window and run it again (section control server)
echo   3. Ctrl+F5 in the browser
echo Undo: run bat\backup_patch_1103_%TS%\restore_patch_1103.bat
pause
exit /b 0

:put
set "REL=%~1"
if not exist "%SRC%\%REL%" ( echo   [MISSING in package] !REL! & echo missing !REL! >> "!LOG!" & set FAILED=1 & goto :eof )
if exist "%TARGET%\%REL%" (
  for %%d in ("%BK%\%REL%") do if not exist "%%~dpd" mkdir "%%~dpd"
  copy /y "%TARGET%\%REL%" "%BK%\%REL%" >nul
) else (
  echo %REL%>>"%BK%\new_files.txt"
)
for %%d in ("%TARGET%\%REL%") do if not exist "%%~dpd" mkdir "%%~dpd"
copy /y "%SRC%\%REL%" "%TARGET%\%REL%" >nul
if errorlevel 1 ( echo   [FAIL] !REL! & set FAILED=1 ) else ( set /a COUNT+=1 & echo ok %REL% >> "%LOG%" )
goto :eof

:bad_target
echo [ERROR] Not a project folder: "%TARGET%"
echo         Expected backend\scargo\src\main\java and toll_demo\toll_web.py inside it.
pause
exit /b 1

:bad_src
echo [ERROR] files folder is missing next to this bat. Extract the whole zip first.
pause
exit /b 1
