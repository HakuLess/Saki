@echo off

REM 雀魂助手 (Saki) 启动脚本
REM 用于构建并运行Compose Multiplatform应用程序

echo 正在启动雀魂助手 (Saki)...

REM 检查Java是否安装
java -version >nul 2>&1
if %errorlevel% neq 0 (
    echo 错误: 未找到Java运行环境。请确保安装了Java 11或更高版本。
    pause
    exit /b 1
)

REM 检查Gradle是否可用
if exist gradlew.bat (
    echo 使用项目Gradle包装器...
    call gradlew.bat :desktopApp:run
) else (
    echo 错误: 未找到Gradle包装器。请确保在正确的目录中运行此脚本。
    pause
    exit /b 1
)

if %errorlevel% neq 0 (
    echo 应用程序启动失败。请检查错误信息。
    pause
    exit /b 1
)

echo 应用程序已退出。
pause