@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [1/4] Checking Python...
set "PYX="
python -c "import sys;sys.exit(0 if (3,10)<=sys.version_info[:2]<=(3,12) else 1)" >nul 2>&1
if not errorlevel 1 set "PYX=python"
if not defined PYX for %%V in (310 311 312) do if not defined PYX if exist "%LocalAppData%\Programs\Python\Python%%V\python.exe" set PYX="%LocalAppData%\Programs\Python\Python%%V\python.exe"
if not defined PYX if exist "%~dp0..\python\ocr_env\Scripts\python.exe" set PYX="%~dp0..\python\ocr_env\Scripts\python.exe"
if not defined PYX if exist "%USERPROFILE%\anaconda3\envs\yolo\python.exe" set PYX="%USERPROFILE%\anaconda3\envs\yolo\python.exe"
if not defined PYX if exist "%USERPROFILE%\anaconda3\python.exe" set PYX="%USERPROFILE%\anaconda3\python.exe"
if not defined PYX for %%V in (3.10 3.11 3.12) do if not defined PYX py -%%V -c "import sys" >nul 2>&1 && set "PYX=py -%%V"
if not defined PYX (
  echo Python 3.10-3.12 was not found. Install from python.org and check "Add to PATH", then run this again.
  pause
  exit /b 1
)
echo Using: %PYX%
echo [2/4] Creating virtual environment (gate_env)...
if not exist gate_env\Scripts\python.exe %PYX% -m venv gate_env
if not exist gate_env\Scripts\python.exe (
  echo Failed to create gate_env.
  pause
  exit /b 1
)
call gate_env\Scripts\activate.bat
python -m pip install --upgrade pip
echo [3/4] Installing PyTorch (CPU) ...
set TV=
if exist torch_version.txt set /p TV=<torch_version.txt
if defined TV (
  pip install torch==%TV% torchvision --index-url https://download.pytorch.org/whl/cpu || pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
) else (
  pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
)
echo [4/4] Installing the other packages...
pip install -r requirements.txt
echo.
echo Checking...
python -c "import torch,cv2,ultralytics,fastapi,uvicorn,psycopg2;print('OK  torch',torch.__version__,'ultralytics',ultralytics.__version__)"
echo.
echo Done. Edit db_url.txt if the DB is not on this PC, then double-click run_toll.bat
pause
