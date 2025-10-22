@echo off
echo Launching Chrome with remote debugging...
"C:\Program Files\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir="%TEMP%\chrome_debug_data" https://game.maj-soul.com/1/
if %errorlevel% == 0 (
    echo Chrome launched successfully!
) else (
    echo Failed to launch Chrome. Trying alternative path...
    "C:\Program Files (x86)\Google\Chrome\Application\chrome.exe" --remote-debugging-port=9222 --user-data-dir="%TEMP%\chrome_debug_data" https://game.maj-soul.com/1/
    if %errorlevel% == 0 (
        echo Chrome launched successfully!
    ) else (
        echo Failed to launch Chrome. Please check your Chrome installation path.
    )
)
pause