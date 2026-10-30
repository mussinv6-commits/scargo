@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
cd /d "%~dp0.."
set "ROOT=%~dp0..\"
title S그룹 새 PC 자동 설치
echo ==============================================
echo   S그룹 새 PC 자동 설치  S Cargo + S-Bot
echo   없는 프로그램은 물어본 뒤 자동으로 설치합니다
echo ==============================================
echo.
where winget >nul 2>&1 && set "WG=1"

echo [1/6] Java 17 확인
set "JOK="
where javac >nul 2>&1 && set "JOK=1"
if not defined JOK if exist "%ProgramFiles%\Eclipse Adoptium\jdk-17*" set "JOK=1"
if not defined JOK if exist "%USERPROFILE%\.jdks\*" set "JOK=1"
if not defined JOK if exist "%ProgramFiles%\Java\jdk*" set "JOK=1"
if defined JOK (
  echo   OK 이미 설치되어 있습니다
) else (
  call :install "Java 17" EclipseAdoptium.Temurin.17.JDK ""
)

echo.
echo [2/6] Node.js 확인
where npm >nul 2>&1
if errorlevel 1 (
  call :install "Node.js" OpenJS.NodeJS.LTS ""
  set "PATH=%ProgramFiles%\nodejs;!PATH!"
) else (
  echo   OK 이미 설치되어 있습니다
)

echo.
echo [3/6] PostgreSQL 확인  포트 5432
call :tcp 5432
if "!TCPOK!"=="1" (
  echo   OK 실행 중입니다
) else (
  echo   PostgreSQL 이 켜져 있지 않습니다. 이미 설치했다면 서비스를 시작해 주세요.
  call :install "PostgreSQL 16  비밀번호 1234 로 설치됩니다" PostgreSQL.PostgreSQL.16 "--mode unattended --unattendedmodeui none --superpassword 1234"
  for /d %%d in ("%ProgramFiles%\PostgreSQL\*") do set "PATH=%%d\bin;!PATH!"
  call :waitpg
)

echo.
echo [4/6] 데이터베이스 만들기
choice /c YN /m "  DB 를 새로 만들까요? 처음 설치하는 PC 면 Y"
if errorlevel 2 (
  echo   건너뜁니다
) else (
  if exist "%ROOT%bat\setup_db.bat" (
    start "DB 설치" /wait cmd /c "%ROOT%bat\setup_db.bat"
  ) else (
    echo   setup_db.bat 이 없습니다. INSTALL.txt 의 DB 항목을 보고 직접 실행해 주세요.
  )
)

echo.
echo [5/6] 웹 화면 패키지 설치  npm install
if exist "%ROOT%frontend\node_modules" (
  echo   OK 이미 설치되어 있습니다
) else (
  pushd "%ROOT%frontend"
  call npm install
  popd
)

echo.
echo [6/6] Python / 번호판 OCR 환경
call :findpy
if not defined PYX (
  echo   Python 3.10~3.12 가 없습니다. 3.10 을 설치합니다.
  call :install "Python 3.10" Python.Python.3.10 "/quiet InstallAllUsers=0 PrependPath=1"
  set "PATH=%LocalAppData%\Programs\Python\Python310;%LocalAppData%\Programs\Python\Python310\Scripts;!PATH!"
  call :findpy
)
if not defined PYX (
  echo   Python 을 못 찾았습니다. python.org 에서 3.10 을 설치하고 Add to PATH 를 체크한 뒤 이 파일을 다시 실행해 주세요.
  echo   번호판 OCR 과 구간 관제는 건너뜁니다. 나머지 기능은 정상 동작합니다.
  goto :shortcut
)
for %%D in ("!PYX!") do set "PATH=%%~dpD;%%~dpDScripts;!PATH!"
echo   Python: !PYX!
if not exist "%ROOT%python\runs\detect\plate_detect_m_expanded-2\weights\best.pt" echo   주의 OCR 모델 파일이 없습니다. 모델1 모델2 압축을 이 폴더 안에 풀어 주세요.
if exist "%ROOT%python\ocr_env\Lib\site-packages\easyocr" (
  echo   OK OCR 환경이 이미 준비되어 있습니다
) else (
  echo   OCR 환경 만드는 중... 10~20분 걸립니다. 창을 닫지 마세요.
  "!PYX!" -m venv "%ROOT%python\ocr_env"
  "%ROOT%python\ocr_env\Scripts\python.exe" -m pip install --upgrade pip
  "%ROOT%python\ocr_env\Scripts\python.exe" -m pip install torch==2.14.0 torchvision==0.29.0 --index-url https://download.pytorch.org/whl/cpu
  if errorlevel 1 "%ROOT%python\ocr_env\Scripts\python.exe" -m pip install torch torchvision --index-url https://download.pytorch.org/whl/cpu
  "%ROOT%python\ocr_env\Scripts\python.exe" -m pip install -r "%ROOT%python\requirements-gate-api.txt"
)
if not exist "%ROOT%toll_demo\models\ocr.pth" echo   주의 구간 관제 모델 zip(_toll_model)을 이 폴더에 아직 안 풀었습니다. 풀어야 구간 관제가 켜집니다.
if exist "%ROOT%toll_demo\setup_toll.bat" (
  echo.
  echo   구간 관제 환경 설치 중...
  call "%ROOT%toll_demo\setup_toll.bat"
)

:shortcut
echo.
echo 바탕화면 바로가기 만드는 중...
if exist "%ROOT%bat\바로가기_만들기.ps1" powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT%bat\바로가기_만들기.ps1"

echo.
echo ==============================================
echo   설치가 끝났습니다
echo   앞으로는 바탕화면 S그룹 아이콘을 열고 [실행] 버튼만 누르면 됩니다
echo ==============================================
choice /c YN /m "지금 바로 실행할까요?"
if errorlevel 2 goto :end
start "" mshta.exe "%ROOT%런처\S그룹_런처.hta"
:end
echo.
pause
exit /b 0

:waitpg
echo   서버가 켜질 때까지 기다리는 중...
for /l %%i in (1,1,30) do (
  call :tcp 5432
  if "!TCPOK!"=="1" exit /b 0
  timeout /t 2 /nobreak >nul
)
exit /b 0

:install
rem %1 이름  %2 winget 아이디  %3 설치 옵션
if not defined WG (
  echo   winget 이 없어 자동 설치를 못 합니다. %~1 을 직접 설치해 주세요.
  exit /b 1
)
choice /c YN /m "  %~1 이 없습니다. 지금 설치할까요?"
if errorlevel 2 (
  echo   건너뜁니다
  exit /b 1
)
if "%~3"=="" (
  winget install -e --id %2 --accept-package-agreements --accept-source-agreements
) else (
  winget install -e --id %2 --accept-package-agreements --accept-source-agreements --override "%~3"
)
exit /b 0

:findpy
set "PYX="
for %%C in ("py -3.10" "py -3.11" "py -3.12" "python") do if not defined PYX call :trypy %%~C
exit /b 0

:trypy
%* -c "import sys;sys.exit(0 if (3,10)<=sys.version_info[:2]<=(3,12) else 1)" >nul 2>&1
if errorlevel 1 exit /b 1
for /f "delims=" %%P in ('%* -c "import sys;print(sys.executable)"') do set "PYX=%%P"
exit /b 0

:tcp
set "TCPOK=0"
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient('127.0.0.1',%1);$c.Close();exit 0}catch{exit 1}"
if not errorlevel 1 set "TCPOK=1"
exit /b 0
