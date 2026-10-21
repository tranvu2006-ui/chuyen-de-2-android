@echo off
chcp 65001 >nul
title Oishi Food - Web Admin Dashboard Server
cd /d "%~dp0"
echo ===================================================
echo   KHOI DONG OISHI FOOD WEB ADMIN DASHBOARD (8080)
echo ===================================================
echo.
echo Dang mo trinh duyet tai: http://localhost:8080/admin.html
start "" "http://localhost:8080/admin.html"
npx --yes http-server -p 8080 -c-1 --cors
pause
