@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
set "ROOT=%~dp0..\..\"
powershell -NoProfile -Command "try{$c=New-Object Net.Sockets.TcpClient;$c.Connect('127.0.0.1',5432);$c.Close();exit 0}catch{exit 1}"
if errorlevel 1 (
  echo PostgreSQL start try...
  powershell -NoProfile -Command "Get-Service | Where-Object {$_.Name -like 'postgresql*'} | Start-Service" >nul 2>&1
  ping -n 7 127.0.0.1 >nul
)
where java >nul 2>&1
if errorlevel 1 call :findjava
where java >nul 2>&1
if errorlevel 1 (
  echo [ERROR] JDK not found. Install JDK 17 or set JAVA_HOME.
  exit /b 1
)
cd /d "%ROOT%backend\scargo"
call gradlew.bat bootRun
exit /b %errorlevel%

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
