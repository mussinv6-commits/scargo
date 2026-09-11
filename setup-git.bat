@echo off
setlocal

set REPO_URL=https://github.com/mussinv6-commits/scargo.git

if not exist ".git" (
    git init
)

git add .
git commit -m "feat: initial backend structure (Spring Boot + JPA + PostgreSQL)"
git branch -M main

git remote get-url origin >nul 2>&1
if errorlevel 1 (
    git remote add origin %REPO_URL%
) else (
    git remote set-url origin %REPO_URL%
)

git push -u origin main

pause
