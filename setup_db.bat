@echo off
setlocal
rem SCARGO single DB name = scargo. ASCII only. No parentheses blocks (they broke the previous version).
set DBNAME=scargo
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
set PGCLIENTENCODING=UTF8
set PGOPTIONS=-c lc_messages=C
set LOG=%~dp0setup_db.log
set D=%~dp0DB
echo ==== %date% %time% ==== > "%LOG%"

where psql >nul 2>nul
if not errorlevel 1 goto have_psql
for /d %%d in ("C:\Program Files\PostgreSQL\*") do if exist "%%d\bin\psql.exe" set "PATH=%%d\bin;%PATH%"
where psql >nul 2>nul
if errorlevel 1 goto no_psql
:have_psql

set /p PGPASSWORD=postgres password: 

psql -d postgres -Atc "select 1 from pg_database where datname='%DBNAME%'" > "%TEMP%\scargo_chk.txt" 2>>"%LOG%"
if errorlevel 1 goto no_connect

set NEED_INSTALL=0
findstr 1 "%TEMP%\scargo_chk.txt" >nul
if errorlevel 1 goto db_missing

psql -d %DBNAME% -Atc "select 1 from information_schema.tables where table_name='container_inspections'" > "%TEMP%\scargo_chk2.txt" 2>>"%LOG%"
findstr 1 "%TEMP%\scargo_chk2.txt" >nul
if not errorlevel 1 goto db_ok

echo.
echo [!] The existing %DBNAME% DB has the OLD schema - no container_inspections table.
echo     The new code needs the integrated schema. Your old DB is NOT deleted:
echo     it is renamed to scargo_old_DATE and a fresh %DBNAME% is created.
echo     Stop the Spring app in STS before continuing.
set /p OK=Continue? y or N: 
if /i not "%OK%"=="y" goto cancelled
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set STAMP=%%i
psql -d postgres -q -c "select pg_terminate_backend(pid) from pg_stat_activity where datname='%DBNAME%' and pid<>pg_backend_pid()" >>"%LOG%" 2>&1
psql -d postgres -v ON_ERROR_STOP=1 -c "ALTER DATABASE %DBNAME% RENAME TO scargo_old_%STAMP%" >>"%LOG%" 2>&1
if errorlevel 1 goto fail
echo [info] old DB kept as scargo_old_%STAMP%
set NEED_INSTALL=1
goto install

:db_missing
set NEED_INSTALL=1
goto install

:db_ok
echo [info] %DBNAME% already has the integrated schema - keeping existing data
goto addons

:install
echo [info] creating %DBNAME% and installing the full schema - 1 to 2 min
psql -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE %DBNAME% ENCODING 'UTF8' TEMPLATE template0" >>"%LOG%" 2>&1
if errorlevel 1 goto fail
psql -d %DBNAME% -v ON_ERROR_STOP=1 -1 -q -f "%D%\SCARGO_Integrated_Full_Install_20261024.sql" >>"%LOG%" 2>&1
if errorlevel 1 goto fail

:addons
echo [1/3] inspection photos table
psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%D%\add_1_features.sql" >>"%LOG%" 2>&1
if errorlevel 1 goto fail
echo [2/3] incidents table
psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%D%\add_2_ops.sql" >>"%LOG%" 2>&1
if errorlevel 1 goto fail
echo [3/3] gate reservations table
psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%D%\add_3_gate.sql" >>"%LOG%" 2>&1
if errorlevel 1 goto fail

set /p SEED=Add rehearsal sample data too? y or N: 
if /i not "%SEED%"=="y" goto done
psql -d %DBNAME% -v ON_ERROR_STOP=1 -q -f "%D%\add_4_seed.sql" >>"%LOG%" 2>&1
if errorlevel 1 goto fail

:done
echo.
echo [OK] %DBNAME% is ready. Restart ScargoApplication in STS.
pause
exit /b 0

:no_psql
echo [ERROR] psql not found. Add the PostgreSQL bin folder to PATH.
pause
exit /b 1

:no_connect
echo [FAIL] cannot connect to PostgreSQL. Check the password and that the server is running.
pause
exit /b 1

:cancelled
echo cancelled.
pause
exit /b 1

:fail
echo.
echo [FAIL] stopped at the step above. Last log lines:
powershell -NoProfile -Command "Get-Content -Tail 12 '%LOG%'"
echo full log: %LOG%
pause
exit /b 1
