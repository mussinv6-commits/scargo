@echo off
setlocal
set "SRC=%~dp0"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format MMdd_HHmm"') do set "D=%%i"
set "DESK=%USERPROFILE%\Desktop"
set "N=SCargo_share_%D%"
set "T=%DESK%\%N%"
echo Make share package: %T%
mkdir "%T%"
echo [1/4] frontend
robocopy "%SRC%frontend" "%T%\frontend" /E /XD node_modules dist /NFL /NDL /NJH /NJS /NP >nul
echo [2/4] backend
robocopy "%SRC%backend" "%T%\backend" /E /XD build bin .gradle .apt_generated .apt_generated_tests .settings out firebase /NFL /NDL /NJH /NJS /NP >nul
echo [3/4] python, DB, docs
robocopy "%SRC%python" "%T%\python" /E /XD __pycache__ runs build dist /NFL /NDL /NJH /NJS /NP >nul
for /d %%d in ("%SRC%Scargo_1*") do robocopy "%%d" "%T%\DB" /E /XF *FINAL*.txt /NFL /NDL /NJH /NJS /NP >nul
copy /y "%SRC%README.md" "%T%\" >nul
copy /y "%SRC%INSTALL.txt" "%T%\" >nul
echo [4/4] zip
tar -a -c -f "%DESK%\%N%.zip" -C "%DESK%" "%N%"
echo.
if exist "%DESK%\%N%.zip" (
  echo DONE: %DESK%\%N%.zip
  explorer /select,"%DESK%\%N%.zip"
) else (
  echo ZIP failed. Folder is ready: %T%  - compress it with Bandizip.
  explorer "%DESK%"
)
echo.
pause
