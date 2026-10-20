@echo off
chcp 65001 > nul
cd /d "%~dp0"
echo === 최종파일 정리 ===
for %%d in (발표자료 기술정리 회고_QA 영상 화면_미리보기 메모 기타) do if not exist "%%d" mkdir "%%d"

rem 발표자료
for %%f in ("못먹어도S카고_발표 자료 (1).pptx" "못먹어도S카고_발표자료_Pretendard.pptx" "SCargo_최종발표.pptx" "시연시나리오_제안.pptx" "기술스택_로고슬라이드.pptx") do if exist %%f move /y %%f "발표자료\" > nul

rem 기술정리 · 기획서
for %%f in ("AMR_디지털트윈_기술정리.pptx" "OCR_과적_기술정리.pptx" "SCargo_기술구성.pptx" "S카고_포트폴리오_기획서.pptx") do if exist %%f move /y %%f "기술정리\" > nul

rem 회고 · QA
for %%f in ("SCargo_KPT회고.pptx" "SCargo_QA_결과서.pptx" "SCargo_수정목록.pptx" "SCargo_예상질문100.pptx") do if exist %%f move /y %%f "회고_QA\" > nul

rem 영상 (맨 위 mp4 전부)
for %%f in (*.mp4) do move /y "%%f" "영상\" > nul
if exist "번호판영상" move "번호판영상" "기타\번호판합성_작업" > nul

rem 화면 미리보기 (html · 3D 이미지)
for %%f in ("AMR_3D_미리보기.html" "S카고_컨트롤타워_지도.html") do if exist %%f move /y %%f "화면_미리보기\" > nul
if exist "컨트롤타워_3D" move "컨트롤타워_3D" "화면_미리보기\컨트롤타워_3D" > nul

rem 메모
for %%f in ("명령어.txt" "db 테이블.txt" "db 테이블_통합_261014.txt") do if exist %%f move /y %%f "메모\" > nul

rem 기타 (글꼴 · 예전 깃허브 스크립트)
for %%f in ("Pretendard_글꼴.zip" "깃허브_업데이트.bat") do if exist %%f move /y %%f "기타\" > nul
if exist "깃허브_정리" move "깃허브_정리" "기타\깃허브_정리" > nul

echo.
echo 완료. 이 파일(정리하기.bat)은 지워도 됩니다.
pause
