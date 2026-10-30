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
set "JAVA_TOOL_OPTIONS=-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto"
cd /d "%ROOT%backend\scargo"
rem (see launcher notes)
rem (see launcher notes)
rem (see launcher notes)
set "STALE=1"
if exist "build\run.args" if exist "build\classes\java\main" powershell -NoProfile -Command "$j=(Get-Item 'build\run.args').LastWriteTime; $n=Get-ChildItem 'src','build.gradle','settings.gradle' -Recurse -File -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1; if($n -and $n.LastWriteTime -gt $j){exit 1}else{exit 0}"
if exist "build\run.args" if exist "build\classes\java\main" if not errorlevel 1 set "STALE=0"
if "!STALE!"=="1" (
  echo [build] source changed - compiling
  call gradlew.bat classes writeRunArgs -x test
  if errorlevel 1 goto :slow
) else (
  echo [fast] up-to-date, skip compile
)
if not exist "build\run.args" goto :slow
echo [run] run java directly
java @build\run.args com.scargo.ScargoApplication
exit /b %errorlevel%

:slow
call gradlew.bat bootRun
exit /b %errorlevel%

:findjava
rem (see launcher notes)
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
  echo        Java auto-detected: !JAVA_HOME!
)
exit /b 0
