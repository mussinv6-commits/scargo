@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
rem 연결키트용 OCR 서버 실행 (4_OCR서버 폴더 기준) - 백엔드(8080)를 먼저 켜세요. 창을 닫으면 꺼집니다.
cd /d "%~dp04_OCR서버"
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
  echo [오류] YOLO / EasyOCR 이 깔린 아나콘다 환경을 못 찾았습니다. 연결방법.txt 의 OCR 서버 준비를 보세요.
  pause & exit /b 1
)
echo 사용 환경: anaconda !ENVNAME!
set "NEED="
for %%P in (fastapi uvicorn pg8000 aiofiles) do if not exist "!SP!\%%P" set "NEED=1"
if defined NEED "!PY!" -m pip install fastapi "uvicorn[standard]" pg8000 aiofiles
echo 모델 불러오는 중... "준비 완료" 가 뜰 때까지 30초~1분 기다리세요.
set GATE_API_ID=admin
set GATE_API_PW=admin1234
if "%PGPASSWORD%"=="" set PGPASSWORD=1234
"!PY!" -m uvicorn gate_api:app --host 0.0.0.0 --port 8001
pause
