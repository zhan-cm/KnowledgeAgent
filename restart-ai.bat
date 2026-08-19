@echo off
chcp 65001 >nul
title 重启 Python AI 服务
cd /d "%~dp0"

echo 停止旧 Python 服务 (8000)...
for /f "tokens=5" %%p in ('netstat -ano ^| findstr ":8000 " ^| findstr "LISTENING"') do (
    taskkill /f /pid %%p >nul 2>&1 && echo   已停止进程 %%p
)
timeout /t 2 /nobreak >nul

echo 启动 Python AI 服务（Ctrl+C 可停止）...
cd /d "%~dp0ai-service"
venv\Scripts\python.exe -m uvicorn app.main:app --port 8000
