@echo off
chcp 65001 >nul
rem [5] 연결키트 만들기: 새 웹 · 백엔드 프로젝트에 AMR을 바로 붙일 수 있게, 지금 쓰는 파일을 AMR_업데이트\연결키트 한 곳에 모은다
rem     몇 번을 실행해도 그때의 최신 파일로 다시 모은다 (원본은 건드리지 않음)
cd /d "%~dp0.."
set "K=%~dp0연결키트"
set "Q=/NFL /NDL /NJH /NJS /NP /R:1 /W:1"
echo 연결키트 모으는 중... (OCR 모델 때문에 1~2분 걸릴 수 있어요)
echo.
echo [1/4] 웹 (3D 관제 화면)
robocopy "frontend\src\components\amr" "%K%\1_웹\components\amr" /MIR %Q% >nul
if errorlevel 8 goto fail
robocopy "frontend\src\components\admin\screens" "%K%\1_웹\screens" AdminAmrTwin.vue %Q% >nul
if errorlevel 8 goto fail
echo [2/4] 백엔드 (com.scargo.amr 패키지)
robocopy "backend\scargo\src\main\java\com\scargo\amr" "%K%\2_백엔드\amr" /MIR %Q% >nul
if errorlevel 8 goto fail
echo [3/4] 관리자 앱 (node_modules · 빌드 결과 제외)
robocopy "amr-app" "%K%\3_앱\amr-app" /MIR /XD node_modules dist build .gradle .idea .cxx /XF *.apk *.aab %Q% >nul
if errorlevel 8 goto fail
echo [4/4] OCR 서버 (코드 · 모델 · 허브 사진)
robocopy "python" "%K%\4_OCR서버" gate_api.py batch_test_images.py plate_ocr_crnn_attn.py plate_ocr_crnn.py requirements.txt %Q% >nul
if errorlevel 8 goto fail
robocopy "python\ocr_model_attn" "%K%\4_OCR서버\ocr_model_attn" /MIR %Q% >nul
if errorlevel 8 goto fail
robocopy "python\runs\detect" "%K%\4_OCR서버\runs\detect" best.pt results.csv args.yaml /S %Q% >nul
if errorlevel 8 goto fail
robocopy "python\허브_수신함" "%K%\4_OCR서버\허브_수신함" /MIR %Q% >nul
if errorlevel 8 goto fail
echo.
echo 완료: %K%
echo 붙이는 방법은 연결키트\연결방법.txt 를 보세요.
explorer "%K%"
pause
exit /b 0
:fail
echo.
echo [오류] 복사 중 문제가 생겼습니다. 이 화면을 캡처해서 보내주세요.
pause
exit /b 1
