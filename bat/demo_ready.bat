@echo off
setlocal
rem Demo data ready: run the DB demo script in one click. ASCII only. Safe to run many times.
set DBNAME=scargo
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
set PGCLIENTENCODING=UTF8
set PGOPTIONS=-c lc_messages=C
set LOG=%~dp0demo_ready.log
set SQL=%~dp0..\DB\demo_ready.sql
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

echo [1/1] applying demo data - checkpoint, incident owners, clear alerts and report stamps
psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%SQL%" >>"%LOG%" 2>&1
if errorlevel 1 goto fail

echo.
echo [OK] demo data is ready. Reload the web page. Restart the backend only if it was off.
pause
exit /b 0

:no_psql
echo [ERROR] psql not found. Add the PostgreSQL bin folder to PATH.
pause
exit /b 1

:fail
echo.
echo [FAIL] stopped. Last log lines:
powershell -NoProfile -Command "Get-Content -Tail 12 '%LOG%'"
pause
exit /b 1
