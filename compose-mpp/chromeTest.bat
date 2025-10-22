@echo off
chcp 65001
echo ========================================
echo Chrome远程调试启动脚本
echo ========================================

echo 检查端口9222占用情况...
netstat -ano | findstr :9222 >nul
if %errorlevel% == 0 (
    echo 警告: 端口9222已被占用，正在查找占用进程...
    for /f "tokens=5" %%a in ('netstat -ano ^| findstr :9222') do (
        echo 尝试结束进程 %%a
        taskkill /f /pid %%a >nul 2>&1
    )
    timeout /t 2 /nobreak >nul
)

echo 关闭所有Chrome进程...
taskkill /f /im chrome.exe >nul 2>&1
timeout /t 2 /nobreak >nul

echo 尝试启动Chrome远程调试...
set CHROME_PATH="C:\Program Files\Google\Chrome\Application\chrome.exe"
if not exist %CHROME_PATH% (
    set CHROME_PATH="C:\Program Files (x86)\Google\Chrome\Application\chrome.exe"
)

if exist %CHROME_PATH% (
    echo 找到Chrome，正在启动...
    start "" %CHROME_PATH% --remote-debugging-port=9222 --user-data-dir="%TEMP%\chrome_debug_data"
    echo Chrome已启动，等待5秒...
    timeout /t 5 /nobreak >nul
    
    echo 测试连接...
    curl -s http://localhost:9222/json/version >nul 2>&1
    if %errorlevel% == 0 (
        echo 连接成功！请在Chrome中打开 https://game.maj-soul.com/1/
        echo 然后启动Saki应用程序进行网络拦截
        start http://localhost:9222
    ) else (
        echo 连接失败，请检查以下几点：
        echo 1. Chrome是否正确启动
        echo 2. 防火墙是否阻止了连接
        echo 3. 端口9222是否被其他程序占用
    )
) else (
    echo 未找到Chrome浏览器，请检查安装路径
    echo 请手动启动Chrome并添加参数：--remote-debugging-port=9222
)

echo.
echo 按任意键退出...
pause >nul