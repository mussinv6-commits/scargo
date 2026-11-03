@echo off
setlocal enabledelayedexpansion
rem Undo patch 1102. apply_patch_1102.bat copies this file into <project>\bat\backup_patch_1102_<time>\ - run it from there.
rem Puts the backed-up files back and deletes the files the patch added. The DB column block_reason is left as is (harmless).
set "BK=%~dp0"
set "TARGET=%~dp0..\.."
if not exist "%BK%backend" if not exist "%BK%new_files.txt" goto not_backup
if not exist "%TARGET%\backend\scargo\src\main\java\com\scargo" goto not_backup
choice /c YN /m "Restore the project files from this backup"
if errorlevel 2 exit /b 0
robocopy "%BK%." "%TARGET%" /S /XF restore_patch_1102.bat new_files.txt /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 ( echo [FAIL] copy back failed & pause & exit /b 1 )
if exist "%BK%new_files.txt" for /f "usebackq delims=" %%r in ("%BK%new_files.txt") do if exist "%TARGET%\%%r" del /q "%TARGET%\%%r"
echo [OK] Restored. Restart the backend and press Ctrl+F5 in the browser.
pause
exit /b 0

:not_backup
echo [ERROR] Run this file from inside bat\backup_patch_1102_^<time^> of the project.
pause
exit /b 1
