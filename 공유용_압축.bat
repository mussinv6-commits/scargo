@echo off
chcp 65001 >nul
setlocal
set "SRC=%~dp0"
set "T=%USERPROFILE%\Desktop\SCargo_share"
if exist "%T%" rmdir /s /q "%T%"
mkdir "%T%"
echo [1/2] 복사 중 (node_modules, 빌드 산출물, 키 파일, amr-app 제외)...
robocopy "%SRC%frontend" "%T%\frontend" /E /XD node_modules dist /NFL /NDL /NJH /NJS >nul
robocopy "%SRC%backend" "%T%\backend" /E /XD build bin .gradle .apt_generated .apt_generated_tests .settings out firebase /NFL /NDL /NJH /NJS >nul
robocopy "%SRC%python" "%T%\python" /E /XD __pycache__ runs build dist /NFL /NDL /NJH /NJS >nul
for /d %%d in ("%SRC%Scargo_1*") do robocopy "%%d" "%T%\DB" /E /NFL /NDL /NJH /NJS >nul
copy /y "%SRC%README.md" "%T%\" >nul
copy /y "%SRC%INSTALL.txt" "%T%\" >nul
echo [2/2] 완료: %T%
echo.
echo 이 폴더를 우클릭 - Bandizip - "SCargo_share.7z" 로 압축 하세요. (7z 는 한글 이름이 안 깨집니다)
echo ZIP 으로 하려면 Bandizip 설정 - 압축 - 파일 이름 인코딩을 "UTF-8" 로 바꾸세요.
goto :end
:err
echo [오류] 위 메시지를 캡처해서 보내주세요.
:end
pause
