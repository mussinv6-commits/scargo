@echo off
chcp 65001 >nul
set SRC=%USERPROFILE%\Desktop\최종파일
set DST=%USERPROFILE%\Desktop\최종파일_백업_%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%
set DST=%DST: =0%
echo 백업 중... %DST%
robocopy "%SRC%" "%DST%" /E /XD node_modules dist-driver /NFL /NDL /NJH /NP /R:1 /W:1
echo.
echo 백업 완료: %DST%
pause
