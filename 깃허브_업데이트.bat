@echo off
chcp 65001 >nul
cd /d "%~dp0"
git add -A
git commit -m "26.10.02 게이트아웃 과적통과 제한, 구내차량 목록, 검사소 명칭 통일, 적재중량 판정 추가"
git push origin HEAD
echo.
echo 완료. 위에 오류가 없으면 깃허브 반영 끝
pause
