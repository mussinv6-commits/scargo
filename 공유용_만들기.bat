@echo off
chcp 65001 >nul
setlocal
set "SRC=%~dp0"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format MMdd_HHmm"') do set "D=%%i"
set "DESK=%USERPROFILE%\Desktop"
set "N=SCargo_share_%D%"
set "T=%DESK%\%N%"
set "T2=%DESK%\%N%_m1"
set "T3=%DESK%\%N%_m2"
echo Make share package: %N%   main + model 2 parts, each small
mkdir "%T%"
echo [1/6] frontend
robocopy "%SRC%frontend" "%T%\frontend" /E /XD node_modules dist /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
echo [2/6] backend
robocopy "%SRC%backend" "%T%\backend" /E /XD build bin .gradle .apt_generated .apt_generated_tests .settings out firebase uploads /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
echo [3/6] python without models and logs, DB, docs
robocopy "%SRC%python" "%T%\python" /E /XD __pycache__ runs ocr_model_attn processed failed build dist /XF *.pptx *.csv *.spec CHANGELOG.md plate_detector_gui_backup*.py crash_log.txt /NFL /NDL /NJH /NJS /NP >nul
for /d %%d in ("%SRC%Scargo_1*") do robocopy "%%d" "%T%\DB" /E /XF *FINAL*.txt /NFL /NDL /NJH /NJS /NP >nul
copy /y "%SRC%README.md" "%T%\" >nul
copy /y "%SRC%INSTALL.txt" "%T%\" >nul
echo [4/6] run files
robocopy "%SRC%AMR_업데이트" "%T%\AMR_업데이트" /E /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%amr-app" "%T%\amr-app" /E /XD node_modules dist android www .gradle build /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
for %%f in ("S그룹_전체실행.bat" "전체_종료.bat" "새PC_설치.bat" "바로가기_만들기.bat" "바로가기_만들기.ps1" "sgroup_run.ico" "sgroup_stop.ico" "setup_db.bat" "scargo_backup.bat") do if exist "%SRC%%%~f" copy /y "%SRC%%%~f" "%T%\" >nul
echo [5/6] OCR models split in 2 parts
robocopy "%SRC%python\runs" "%T2%\python\runs" /E /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%python\ocr_model_attn" "%T3%\python\ocr_model_attn" /E /NFL /NDL /NJH /NJS /NP >nul
echo [6/6] zip
tar -a -c -f "%DESK%\%N%.zip" -C "%DESK%" "%N%"
tar -a -c -f "%DESK%\%N%_모델1.zip" -C "%T2%" python
tar -a -c -f "%DESK%\%N%_모델2.zip" -C "%T3%" python
echo.
if exist "%DESK%\%N%.zip" (
  rd /s /q "%T%" "%T2%" "%T3%" 2>nul
  echo DONE. 3 files on Desktop:
  echo   %N%.zip        main
  echo   %N%_모델1.zip  OCR model 1
  echo   %N%_모델2.zip  OCR model 2
  echo On the other PC: unzip main, then unzip both model zips INTO the main folder, then run 새PC_설치.bat
  explorer /select,"%DESK%\%N%.zip"
) else (
  echo ZIP failed. Folder is ready: %T%  - compress it with Bandizip.
  explorer "%DESK%"
)
echo.
pause
