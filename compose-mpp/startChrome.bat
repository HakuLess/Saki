@echo off
echo 关闭所有Chrome进程...
taskkill /f /im chrome.exe >nul 2>&1
timeout /t 2 /nobreak >nul

echo 启动Chrome远程调试模式...
"C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir="%TEMP%\chrome_debug_data" https://game.maj-soul.com/1/

echo Chrome已启动，请检查是否成功打开了雀魂游戏页面。
echo 如果没有打开，请检查Chrome安装路径是否正确。
pause