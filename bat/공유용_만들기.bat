@echo off
chcp 65001 >nul
setlocal
set "SRC=%~dp0..\"
for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format MMdd_HHmm"') do set "D=%%i"
set "DESK=%USERPROFILE%\Desktop"
set "N=SCargo_share_%D%"
set "T=%DESK%\%N%"
echo Make share package: %N%   (main zip + small model zip, because of KakaoTalk size limit)
mkdir "%T%"
echo [1/7] frontend
robocopy "%SRC%frontend" "%T%\frontend" /E /XD node_modules dist dist-driver _색상변경전_백업 /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
echo [2/7] backend
robocopy "%SRC%backend" "%T%\backend" /E /XD build bin .gradle .apt_generated .apt_generated_tests .settings out firebase uploads /XF *.bak *.log /NFL /NDL /NJH /NJS /NP >nul
echo [3/7] python without logs
robocopy "%SRC%python" "%T%\python" /E /XD __pycache__ runs ocr_model_attn processed failed build dist /XF *.pptx *.csv *.spec CHANGELOG.md plate_detector_gui_backup*.py crash_log.txt /NFL /NDL /NJH /NJS /NP >nul
copy /y "%SRC%README.md" "%T%\" >nul
copy /y "%SRC%INSTALL.txt" "%T%\" >nul
echo [4/7] DB, AMR, app
robocopy "%SRC%DB" "%T%\DB" /E /XF *.bak *.log /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%AMR_업데이트" "%T%\AMR_업데이트" /E /XF *.bak 1_적용하기.bat 3_깃허브_업로드.bat 4_앱_빌드.bat 5_연결키트_만들기.bat /XD 연결키트 문서 /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%amr-app" "%T%\amr-app" /E /XD node_modules dist android www .gradle build /XF *.bak /NFL /NDL /NJH /NJS /NP >nul
echo [5/7] launcher, toll section control   (ocr.pth 195MB is left out - KakaoTalk size limit)
robocopy "%SRC%런처" "%T%\런처" /E /XD logs /XF *.bak sp_*.png /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%toll_demo" "%T%\toll_demo" /E /XD gate_env __pycache__ /XF *.bak *.log recognize_all.bat seed_data.bat ocr.pth /NFL /NDL /NJH /NJS /NP >nul
mkdir "%T%\bat"
for %%f in ("새PC_설치.bat" "setup_db.bat" "바로가기_만들기.ps1" "sgroup_run.ico") do if exist "%SRC%bat\%%~f" copy /y "%SRC%bat\%%~f" "%T%\bat\" >nul
echo [6/7] OCR models
robocopy "%SRC%python\runs" "%T%\python\runs" /E /NFL /NDL /NJH /NJS /NP >nul
robocopy "%SRC%python\ocr_model_attn" "%T%\python\ocr_model_attn" /E /NFL /NDL /NJH /NJS /NP >nul
echo [7/7] zip
tar -a -c -f "%DESK%\%N%.zip" -C "%DESK%" "%N%"
echo.
if exist "%SRC%toll_demo\models\ocr.pth" (
  mkdir "%DESK%\toll_tmp\toll_demo\models"
  copy /y "%SRC%toll_demo\models\ocr.pth" "%DESK%\toll_tmp\toll_demo\models\" >nul
  tar -a -c -f "%DESK%\%N%_toll_model.zip" -C "%DESK%\toll_tmp" toll_demo
  rd /s /q "%DESK%\toll_tmp" 2>nul
)
if exist "%DESK%\%N%.zip" (
  rd /s /q "%T%" 2>nul
  echo DONE. 2 files on the Desktop:
  echo    %N%.zip            ... main
  echo    %N%_toll_model.zip ... section control model only
  echo Other PC: put BOTH zips in one folder and unzip them there, then run bat\새PC_설치.bat
  explorer /select,"%DESK%\%N%.zip"
) else (
  echo ZIP failed. Folder is ready: %T%  - compress it with Bandizip.
  explorer "%DESK%"
)
echo.
pause
