@echo off
chcp 65001 >nul
rem 26.10.30 상대 백엔드 소스를 합치기용으로 복사 (합치기 끝나면 메모\백엔드_1030 폴더는 지워도 됨)
set SRC=C:\Users\user\Desktop\SCargo_261030\backend\scargo\src\main\java
set DST=C:\Users\user\Desktop\최종파일\메모\백엔드_1030\java
robocopy "%SRC%" "%DST%" /E /NFL /NDL /NJH /NJS
echo.
echo 복사 끝. 이 창을 닫으면 됨.
pause
