@echo off
chcp 65001 >nul
REM 26.10.20 안 쓰는 파일을 _안쓰는파일 한 곳으로 "이동"만 합니다 (삭제 안 함)
cd /d "%~dp0"
if not exist "_안쓰는파일" mkdir "_안쓰는파일"
for %%F in ("AMR_업데이트\AMR_적용.zip" "AMR_업데이트\amr_link.bundle" "기타\깃허브_정리" "기타\Pretendard_글꼴.zip") do (
  if exist %%F move %%F "_안쓰는파일\" >nul && echo 이동: %%F
)
if exist "..\파이널\_중복_확인후삭제" move "..\파이널\_중복_확인후삭제" "_안쓰는파일\" >nul && echo 이동: 파이널\_중복_확인후삭제
REM DB는 Scargo_1차2라통합DB테이블 한 곳: 최신(11차)만 두고 9차는 _안쓰는파일\이전DB 로
if not exist "_안쓰는파일\이전DB" mkdir "_안쓰는파일\이전DB"
if exist "Scargo_1차2라통합DB테이블\Scargo_최종통합테이블(9차수정).txt" move "Scargo_1차2라통합DB테이블\Scargo_최종통합테이블(9차수정).txt" "_안쓰는파일\이전DB\" >nul
if exist "Scargo_1차2라통합DB테이블\SCARGO_9차_테이블_컬럼_관계설명서.html" move "Scargo_1차2라통합DB테이블\SCARGO_9차_테이블_컬럼_관계설명서.html" "_안쓰는파일\이전DB\" >nul
for %%F in ("Scargo_최종통합테이블(11차수정_더미데이터).txt" "Scargo_최종통합테이블(11차수정_테이블만).txt" "시연_씰_더미.sql") do (
  if exist %%F move %%F "Scargo_1차2라통합DB테이블\" >nul && echo DB 폴더로 이동: %%F
)
echo 끝. _안쓰는파일 폴더를 확인하세요.
pause
