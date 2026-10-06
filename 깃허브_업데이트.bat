@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist ".git" (
  echo [1/5] git 저장소 만들기
  git init
  git branch -M main
  git remote add origin https://github.com/mussinv6-commits/scargo.git
  git fetch origin
  git reset --soft origin/main
)
echo [2/5] 변경 파일 추가
git add -A
echo [3/5] 커밋
git commit -m "26.10.06 최종파일: 구내차량 DB동기화, 재계량 목록, 로그인 유지, 글꼴 통일, QA 결과서"
echo [4/5] 깃허브 업로드
git push origin main
echo.
echo [5/5] 완료. 위에 오류가 없으면 깃허브 반영 끝
pause
