@echo off
REM scargo 프로젝트 git 초기화 스크립트
REM 사용 전에 GitHub에서 빈 저장소(scargo)를 먼저 만드세요 (README/gitignore 체크 해제)
REM 그 다음 아래 REPO_URL을 본인 저장소 주소로 바꾸고 이 파일을 scargo 폴더에서 더블클릭 실행하세요.

set REPO_URL=https://github.com/mussinv6-commits/scargo.git

git init
git add .
git commit -m "Initial commit"
git branch -M main
git remote add origin %REPO_URL%
git push -u origin main

pause
