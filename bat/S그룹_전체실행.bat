@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title S Group 전체 실행
set "ROOT=%~dp0..\"
echo ==============================================
echo   S Group 전체 실행 : 백엔드 - OCR - 웹 순서
echo ==============================================
echo.

rem ---------- [0] 사전 점검 ----------
where java >nul 2>&1
if errorlevel 1 call :findjava
where java >nul 2>&1
if errorlevel 1 (
  echo [오류] JDK 를 찾지 못했습니다. STS 에서 쓰는 JDK 폴더 위치를 알려 주세요.
  echo        또는 JAVA_HOME 환경변수에 JDK 17 폴더를 지정하세요.
  pause & exit /b 1
)
where npm >nul 2>&1
if errorlevel 1 (
  echo [오류] Node.js 를 찾지 못했습니다. Node.js LTS 설치를 확인하세요.
  pause & exit /b 1
)
call :tcp 5432
if errorlevel 1 (
  echo [확인] PostgreSQL 이 꺼져 있습니다. 서비스를 켜는 중...
  powershell -NoProfile -Command "Get-Service | Where-Object {$_.Name -like 'postgresql*'} | Start-Service" >nul 2>&1
  timeout /t 6 /nobreak >nul
  call :tcp 5432
  if errorlevel 1 (
    echo [오류] PostgreSQL 이 켜지지 않았습니다. 관리자 권한으로 다시 실행하거나 서비스를 직접 켜세요.
    pause & exit /b 1
  )
)
echo [0/3] 점검 완료 : Java, Node.js, PostgreSQL 정상
echo.

rem ---------- [1] 백엔드 Spring Boot 8080 ----------
call :http http://127.0.0.1:8080/api/amr/kpi
if not errorlevel 1 (
  echo [1/3] 백엔드 이미 실행 중 : 건너뜀
) else (
  echo [1/3] 백엔드 시작 중... 처음에는 1~3분 걸릴 수 있습니다
  start "S-Bot 백엔드 8080" /D "%ROOT%backend\scargo" cmd /k gradlew.bat bootRun
  call :wait http://127.0.0.1:8080/api/amr/kpi 300 백엔드
  if errorlevel 1 (
    echo [오류] 백엔드가 5분 안에 켜지지 않았습니다. 백엔드 창의 오류 메시지를 확인하세요.
    pause & exit /b 1
  )
  echo        백엔드 준비 완료
)
echo.

rem ---------- [2] OCR 서버 8001 ----------
call :http http://127.0.0.1:8001/docs
if not errorlevel 1 (
  echo [2/3] OCR 서버 이미 실행 중 : 건너뜀
) else (
  echo [2/3] OCR 서버 시작 중... 모델을 불러오는 데 30초~1분 걸립니다
  start "S-Bot OCR 8001" /D "%ROOT%AMR_업데이트" cmd /k call "2_OCR서버_실행.bat"
  call :wait http://127.0.0.1:8001/docs 240 OCR
  if errorlevel 1 (
    echo [주의] OCR 서버가 켜지지 않았습니다. OCR 창의 메시지를 확인하세요. 웹은 계속 켭니다.
  ) else (
    echo        OCR 서버 준비 완료
  )
)
echo.

rem ---------- [3] 웹 서버 5173 ----------
call :http http://localhost:5173
if not errorlevel 1 (
  echo [3/3] 웹 서버 이미 실행 중 : 건너뜀
) else (
  echo [3/3] 웹 서버 시작 중...
  if exist "%ROOT%frontend\node_modules" (
    start "S-Bot 웹 5173" /D "%ROOT%frontend" cmd /k npm run dev
  ) else (
    start "S-Bot 웹 5173" /D "%ROOT%frontend" cmd /k "npm install && npm run dev"
  )
  call :wait http://localhost:5173 180 웹
  if errorlevel 1 (
    echo [오류] 웹 서버가 켜지지 않았습니다. 웹 창의 오류 메시지를 확인하세요.
    pause & exit /b 1
  )
  echo        웹 서버 준비 완료
)
echo.

echo 브라우저를 엽니다 : http://localhost:5173
start "" http://localhost:5173
echo.
echo 모두 켜졌습니다. 종료는 전체_종료.bat 을 실행하세요. 이 창은 닫아도 됩니다.
pause
exit /b 0

rem ====================== 함수 ======================
:findjava
rem PATH 에 Java 가 없을 때 STS / IntelliJ / 설치형 JDK 폴더에서 JDK 17 우선으로 자동 탐지
set "J17=" & set "JBIN="
for %%R in ("%USERPROFILE%\.jdks" "%USERPROFILE%\.gradle\jdks" "%ProgramFiles%\Java" "%ProgramFiles%\Eclipse Adoptium" "%ProgramFiles%\Microsoft" "%ProgramFiles%\Amazon Corretto" "%ProgramFiles%\Zulu" "%ProgramFiles%\BellSoft") do (
  if exist "%%~R" (
    for /f "delims=" %%J in ('dir /b /s "%%~R\javac.exe" 2^>nul') do (
      set "JBIN=%%~dpJ"
      echo "%%~dpJ" | findstr "17" >nul && set "J17=%%~dpJ"
    )
  )
)
if defined J17 set "JBIN=!J17!"
if defined JBIN (
  for %%A in ("!JBIN!..") do set "JAVA_HOME=%%~fA"
  set "PATH=!JAVA_HOME!\bin;!PATH!"
  echo        Java 자동 탐지 : !JAVA_HOME!
)
exit /b 0

:tcp
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',%~1);$c.Close();exit 0}catch{exit 1}"
exit /b %errorlevel%

:http
powershell -NoProfile -Command "try{Invoke-WebRequest -UseBasicParsing -TimeoutSec 3 '%~1' | Out-Null;exit 0}catch{if($_.Exception.Response){exit 0}else{exit 1}}"
exit /b %errorlevel%

:wait
set /a "N=0"
:wait_loop
call :http %1
if !errorlevel! equ 0 exit /b 0
set /a N+=4
if !N! geq %2 exit /b 1
timeout /t 4 /nobreak >nul
echo        %~3 기다리는 중... !N!초
goto wait_loop
