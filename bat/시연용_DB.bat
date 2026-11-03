@echo off
setlocal enabledelayedexpansion
rem Demo DB one-click: apply add_6, 9-15 and demo_ready.sql to the scargo DB. ASCII only. Safe to re-run.
set DBNAME=scargo
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
set PGCLIENTENCODING=UTF8
set PGOPTIONS=-c lc_messages=C
set LOG=%~dp0demo_db.log
set DBDIR=%~dp0..\DB
echo ==== %date% %time% ==== > "%LOG%"

where psql >nul 2>nul
if not errorlevel 1 goto have_psql
for /d %%d in ("C:\Program Files\PostgreSQL\*") do if exist "%%d\bin\psql.exe" set "PATH=%%d\bin;%PATH%"
where psql >nul 2>nul
if errorlevel 1 goto no_psql
:have_psql

if defined PGPASSWORD goto have_pw
set /p PGPASSWORD=postgres password: 
:have_pw

set FAILED=0
set COUNT=0
for %%f in ("%DBDIR%\add_6_*.sql" "%DBDIR%\add_9_*.sql" "%DBDIR%\add_10_*.sql" "%DBDIR%\add_11_*.sql" "%DBDIR%\add_12_*.sql" "%DBDIR%\add_13_*.sql" "%DBDIR%\add_14_*.sql" "%DBDIR%\add_15_*.sql" "%DBDIR%\demo_ready.sql") do (
  echo applying %%~nxf
  echo ---- %%~nxf >> "%LOG%"
  psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%%f" >>"%LOG%" 2>&1
  if errorlevel 1 ( echo   [FAIL] %%~nxf - see log & set FAILED=1 ) else ( echo   [OK] )
  set /a COUNT+=1
)
echo.
if "%COUNT%"=="0" ( echo [ERROR] no SQL files found in %DBDIR% & pause & exit /b 1 )
if "%FAILED%"=="1" ( echo Some scripts failed. Log: %LOG% ) else ( echo [OK] demo DB ready - %COUNT% scripts. Restart the backend and press Ctrl+F5 in the browser. )
pause
exit /b 0

:no_psql
echo [ERROR] psql not found. Add the PostgreSQL bin folder to PATH.
pause
exit /b 1
