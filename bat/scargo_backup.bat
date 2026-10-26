@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion
rem ===== SCARGO DB + uploads backup (keeps last 7 days) =====
rem Needs: pg_dump on PATH, and a password via %APPDATA%\postgresql\pgpass.conf or PGPASSWORD
set DB=scargo_demo
set PGUSER=postgres
set PGHOST=localhost
set PGPORT=5432
set UPLOADS=C:\Users\user\Desktop\최종파일\backend\scargo\uploads
set BACKUP_DIR=C:\Users\user\Desktop\최종파일\백업
set KEEP_DAYS=7

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd_HHmm"') do set STAMP=%%i
if not exist "%BACKUP_DIR%" mkdir "%BACKUP_DIR%"

pg_dump -h %PGHOST% -p %PGPORT% -U %PGUSER% -F c -f "%BACKUP_DIR%\%DB%_%STAMP%.dump" %DB%
if errorlevel 1 ( echo [FAIL] pg_dump & exit /b 1 )

if exist "%UPLOADS%" powershell -NoProfile -Command "Compress-Archive -Path '%UPLOADS%\*' -DestinationPath '%BACKUP_DIR%\uploads_%STAMP%.zip' -Force"

forfiles /p "%BACKUP_DIR%" /m *.dump /d -%KEEP_DAYS% /c "cmd /c del @path" 2>nul
forfiles /p "%BACKUP_DIR%" /m *.zip  /d -%KEEP_DAYS% /c "cmd /c del @path" 2>nul
echo [OK] %STAMP%
