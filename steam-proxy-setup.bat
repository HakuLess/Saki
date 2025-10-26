@echo off
echo Steam 雀魂代理配置脚本
echo ========================

echo 1. 确保 MITM 代理已启动在 127.0.0.1:8899
echo 2. 安装 Proxifier (如果未安装)
echo 3. 配置 Proxifier 规则

echo.
echo 步骤1: 启动 Steam (TCP 模式)
echo 关闭现有 Steam 进程...
taskkill /f /im steam.exe 2>nul

echo 等待 3 秒...
timeout /t 3 /nobreak >nul

echo 启动 Steam (强制 TCP 模式)...
start "" "C:\Program Files (x86)\Steam\steam.exe" -tcp

echo.
echo 步骤2: Proxifier 配置说明
echo ========================
echo 1. 打开 Proxifier
echo 2. Profile -> Proxy Servers -> Add
echo    - Address: 127.0.0.1
echo    - Port: 8899  
echo    - Protocol: HTTPS
echo 3. Profile -> Proxification Rules -> Add
echo    - Name: Steam Mahjong
echo    - Applications: steam.exe
echo    - Target hosts: *.maj-soul.com; *.mahjongsoul.com; *.yo-star.com
echo    - Action: Proxy HTTPS 127.0.0.1:8899
echo 4. 启用规则并保存配置

echo.
echo 配置完成后，在 Steam 中启动雀魂即可开始拦截
pause