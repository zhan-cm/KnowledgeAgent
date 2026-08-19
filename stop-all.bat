@echo off
chcp 65001 >nul
title KnowledgeAgent 一键停止
cd /d "%~dp0"

echo 停止 Java 后端 (8080) 与 Python 服务 (8000)...
set found=0
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8080 " ^| findstr "LISTENING"') do (
    taskkill /f /pid %%p >nul 2>&1 && echo   已停止后端进程 %%p && set found=1
)
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8000 " ^| findstr "LISTENING"') do (
    taskkill /f /pid %%p >nul 2>&1 && echo   已停止 Python 进程 %%p && set found=1
)
if "%found%"=="0" echo   未发现运行中的服务
echo.
echo 提示：若提示"拒绝访问"，请以管理员身份运行本脚本。
echo.
set /p stopdocker=是否同时停止 PostgreSQL/Redis 容器？(y/n):
if /i "%stopdocker%"=="y" (
    docker compose stop
    echo   容器已停止（下次 start-all.bat 会自动重启）
)
echo 完成。
pause
