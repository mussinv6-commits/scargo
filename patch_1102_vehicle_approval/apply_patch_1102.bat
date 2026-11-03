@echo off
setlocal enabledelayedexpansion
rem Patch 1102 (vehicle entry approval): gate entry + dispatch check trucks.entry_approval. ASCII only. Safe to re-run.
rem Usage: apply_patch_1102.bat [project folder]   (project folder = the folder that has backend, frontend, DB)
rem Every file is backed up to <project>\bat\backup_patch_1102_<time> before it is overwritten.
set "SRC=%~dp0files"
set "LOG=%~dp0apply_patch_1102.log"
set "TARGET=%~1"
if "%TARGET%"=="" if exist "%~dp0..\backend\scargo\src\main\java" set "TARGET=%~dp0.."
if "%TARGET%"=="" (
  echo Project folder = the folder that has backend, frontend, DB
  set /p "TARGET=Drag the project folder here and press Enter: "
)
if "%TARGET%"=="" goto bad_target
set "TARGET=%TARGET:"=%"
if "%TARGET:~-1%"=="\" set "TARGET=%TARGET:~0,-1%"
if not exist "%TARGET%\backend\scargo\src\main\java\com\scargo" goto bad_target
if not exist "%SRC%\backend" goto bad_src

for /f %%t in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmmss"') do set "TS=%%t"
set "BK=%TARGET%\bat\backup_patch_1102_%TS%"
mkdir "%BK%" >nul 2>nul
echo ==== %date% %time% ==== > "%LOG%"
echo target: %TARGET% >> "%LOG%"
echo backup: %BK% >> "%LOG%"

echo [1/2] Copying files (backup: bat\backup_patch_1102_%TS%)
set FAILED=0
set COUNT=0
for %%p in (
  backend\scargo\src\main\java\com\scargo\amr\controller\SbotController.java
  backend\scargo\src\main\java\com\scargo\amr\dto\AmrDtos.java
  backend\scargo\src\main\java\com\scargo\amr\entity\HubVehicleLog.java
  backend\scargo\src\main\java\com\scargo\amr\service\AmrService.java
  backend\scargo\src\main\java\com\scargo\amr\service\HubVehicleService.java
  backend\scargo\src\main\java\com\scargo\ops\VehicleUsePolicy.java
  backend\scargo\src\main\java\com\scargo\ops\controller\CarrierController.java
  backend\scargo\src\main\java\com\scargo\ops\controller\V9WebController.java
  backend\scargo\src\main\java\com\scargo\service\MappingService.java
  backend\scargo\src\test\java\com\scargo\amr\service\HubGateEntryExitTests.java
  backend\scargo\src\test\java\com\scargo\ops\DriverApprovalVehicleIndependenceTests.java
  backend\scargo\src\test\java\com\scargo\ops\OrderDispatchValidationTests.java
  backend\scargo\src\test\java\com\scargo\ops\VehicleUsePolicyTests.java
  backend\scargo\src\test\java\com\scargo\service\BackendMergeTests.java
  frontend\src\components\admin\screens\AdminGateSeal.vue
  frontend\src\components\amr\amrScene3d.js
  frontend\src\components\amr\amrSim.js
  frontend\src\scargo2\screens\W07.vue
  frontend\src\scargo2\screens\W08.vue
  amr-app\src\main.js
  amr-app\src\twin\amrScene3d.js
  amr-app\src\twin\amrSim.js
) do call :put "%%p"
rem DB file name is not ASCII, so pick it up by pattern
for %%f in ("%SRC%\DB\add_14_*.sql") do call :put "DB\%%~nxf"
copy /y "%~dp0restore_patch_1102.bat" "%BK%\" >nul
echo   !COUNT! files copied
echo.

echo [2/2] DB migration (DB\add_14_*.sql: hub_vehicle_logs.block_reason)
echo   The backend also adds the column on start, but the CHECK constraint comes only from this script.
choice /c YN /m "  Apply it to your LOCAL DB now"
if errorlevel 2 goto done
set DBNAME=scargo
set /p "DBNAME=  DB name [scargo]: "
if "%DBNAME%"=="" set DBNAME=scargo
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
set PGCLIENTENCODING=UTF8
set PGOPTIONS=-c lc_messages=C
where psql >nul 2>nul
if not errorlevel 1 goto have_psql
for /d %%d in ("C:\Program Files\PostgreSQL\*") do if exist "%%d\bin\psql.exe" set "PATH=%%d\bin;%PATH%"
where psql >nul 2>nul
if errorlevel 1 ( echo   [ERROR] psql not found. Run DB\add_14_*.sql yourself later. & set FAILED=1 & goto done )
:have_psql
if not defined PGPASSWORD set /p "PGPASSWORD=  postgres password: "
for %%f in ("%TARGET%\DB\add_14_*.sql") do (
  echo ---- %%~nxf >> "%LOG%"
  psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%%f" >>"%LOG%" 2>&1
  if errorlevel 1 ( echo   [FAIL] DB migration - see !LOG! & set FAILED=1 ) else ( echo   [OK] DB migration )
)

:done
echo.
if "%FAILED%"=="1" ( echo Some steps failed. Log: !LOG! ) else ( echo [OK] Patch applied. )
echo Next: restart the backend (launcher recompiles changed sources), then Ctrl+F5 in the browser.
echo Undo: run bat\backup_patch_1102_%TS%\restore_patch_1102.bat
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
echo         Expected backend\scargo\src\main\java inside it.
pause
exit /b 1

:bad_src
echo [ERROR] files folder is missing next to this bat. Extract the whole zip first.
pause
exit /b 1
