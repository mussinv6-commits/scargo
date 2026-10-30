@echo off
chcp 65001 >nul
setlocal
set PGCLIENTENCODING=UTF8
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
where psql >nul 2>nul
if not errorlevel 1 goto have_psql
for /d %%d in ("C:\Program Files\PostgreSQL\*") do if exist "%%d\bin\psql.exe" set "PATH=%%d\bin;%PATH%"
:have_psql
set /p PGPASSWORD=postgres password: 
psql -d scargo -f "%~dp0rename.sql"
echo.
echo Done. Check the 3 rows above (company_id 5, 10, 13).
pause
