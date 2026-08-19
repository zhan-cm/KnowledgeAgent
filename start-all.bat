@echo off
chcp 65001 >nul
title KnowledgeAgent 一键启动
cd /d "%~dp0"

echo ============================================
echo   KnowledgeAgent 一键启动
echo ============================================

echo [1/4] 检查 Docker 引擎...
set tries=0
:checkdocker
docker info >nul 2>&1
if not errorlevel 1 goto dockerready
if not defined dockerstarted (
    set dockerstarted=1
    echo   正在启动 Docker Desktop（等待引擎就绪，最长 3 分钟）...
    powershell -NoProfile -Command "Start-Process 'C:\Program Files\Docker\Docker\Docker Desktop.exe'"
)
set /a tries+=1
if %tries% geq 18 (
    echo   [错误] Docker 引擎 3 分钟未就绪，请手动打开 Docker Desktop 后重试
    pause
    exit /b 1
)
timeout /t 10 /nobreak >nul
goto checkdocker
:dockerready
echo   引擎就绪

echo [2/4] 启动 PostgreSQL + Redis 容器...
docker compose up -d

echo [3/4] 启动 Java 后端（独立窗口）...
start "KnowledgeAgent-Backend" cmd /k "cd /d %~dp0 && mvnw.cmd spring-boot:run"

echo [4/4] 启动 Python AI 服务（独立窗口）...
start "KnowledgeAgent-AI" cmd /k "cd /d %~dp0ai-service && venv\Scripts\python.exe -m uvicorn app.main:app --port 8000"

echo.
echo 等待后端就绪（最长 2 分钟）...
set t=0
:waitjava
curl -s -o nul http://localhost:8080/actuator/health
if not errorlevel 1 goto javaready
set /a t+=1
if %t% geq 24 (
    echo   [警告] 后端 2 分钟未就绪，请查看 Backend 窗口日志
    goto done
)
timeout /t 5 /nobreak >nul
goto waitjava
:javaready
echo   Java 后端就绪 ✓（Python 加载模型需数十秒，稍候即可提问）
:done
echo.
echo 浏览器将打开 http://localhost:8080
start http://localhost:8080
echo 完成！本窗口可关闭，不影响服务运行。
pause
