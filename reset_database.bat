@echo off
chcp 65001 >nul
title Oishi Food - Don Sach Du Lieu Firebase
cls
echo ============================================================
echo    OISHI FOOD - DỌN SẠCH DỮ LIỆU TEST TRÊN FIREBASE
echo ============================================================
echo.
echo CANH BAO: Thao tac nay se xoa toan bo du lieu thu nghiem
echo (Cua hang, Don hang, Tai xe, Khach hang, Bep...)
echo va CHI GIU LAI duy nhat tai khoan: admin@gmail.com!
echo.
set /p confirm="Ban co chac chan muon xoa sach khong? (Y/N): "
if /i "%confirm%" NEQ "Y" (
    echo.
    echo Da huy thao tac. Khong co du lieu nao bi xoa.
    echo.
    pause
    exit /b 0
)

echo.
echo Dang tien hanh don sach du lieu, vui long cho trong giay lat...
echo.

python "%~dp0scripts\reset_firebase_data.py"

echo.
pause
