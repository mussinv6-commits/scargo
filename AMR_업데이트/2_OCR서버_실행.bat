@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
rem [2] 번호판 OCR 서버(gate_api.py, 8001) 실행 - 백엔드(8080)를 먼저 켜세요. 창을 닫으면 꺼집니다.
echo OCR 서버 준비 중... (1차 OCR 아나콘다 환경 찾는 중)
cd /d "%~dp0..\python"
set "A=%USERPROFILE%\anaconda3"
set "PY="
for %%E in (yolo plate_build plate_dist its) do (
  if not defined PY if exist "%A%\envs\%%E\Lib\site-packages\ultralytics" if exist "%A%\envs\%%E\Lib\site-packages\easyocr" (
    set "PY=%A%\envs\%%E\python.exe" & set "ENVNAME=%%E" & set "SP=%A%\envs\%%E\Lib\site-packages"
  )
)
if not defined PY if exist "%A%\Lib\site-packages\ultralytics" if exist "%A%\Lib\site-packages\easyocr" (
  set "PY=%A%\python.exe" & set "ENVNAME=base" & set "SP=%A%\Lib\site-packages"
)
if not defined PY (
  echo [오류] YOLO / EasyOCR 이 깔린 아나콘다 환경을 못 찾았습니다. 이 화면을 캡처해서 보내주세요.
  pause & exit /b 1
)
echo 사용 환경: anaconda !ENVNAME!
set "NEED="
for %%P in (fastapi uvicorn pg8000 aiofiles) do if not exist "!SP!\%%P" set "NEED=1"
if defined NEED (
  echo 서버 패키지 설치 중 ^(처음 한 번만, 1~2분^): fastapi uvicorn pg8000 aiofiles
  "!PY!" -m pip install fastapi "uvicorn[standard]" pg8000 aiofiles
)
echo 모델 불러오는 중... "준비 완료" 가 뜰 때까지 30초~1분 기다리세요.
set GATE_API_ID=admin
set GATE_API_PW=admin1234
if "%PGPASSWORD%"=="" set PGPASSWORD=1234
"!PY!" -m uvicorn gate_api:app --host 0.0.0.0 --port 8001
pause
