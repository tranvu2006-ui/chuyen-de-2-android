@echo off
chcp 65001 >nul
title OISHI FOOD - Khoi Tao Du Lieu Mau Firebase

echo =====================================================================
echo    OISHI FOOD - DANG KHOI TAO DU LIEU MAU CHUAN TREN FIREBASE...
echo =====================================================================
echo.

where python >nul 2>nul
if %errorlevel% equ 0 (
    python scripts\seed_sample_data.py
) else (
    "C:\Users\Fuck\AppData\Local\Programs\Python\Python311\python.exe" scripts\seed_sample_data.py
)

echo.
echo =====================================================================
echo    DA HOAN TAT! BAN CO THE DANG NHAP VA TEST APP NGAY BAY GIO.
echo =====================================================================
echo.
pause
