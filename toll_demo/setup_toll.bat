@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo [1/4] Checking Python...
python --version || (echo Python 3.10-3.12 is required. Install from python.org and check "Add to PATH". & pause & exit /b 1)
echo [2/4] Creating virtual environment (gate_env)...
if not exist gate_env\Scripts\python.exe python -m venv gate_env
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
