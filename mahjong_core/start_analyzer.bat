@echo off

rem 启动雀魂分析器服务

echo 启动雀魂分析器服务...

rem 检查Python是否安装
python --version >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo 错误: 未找到Python。请确保已安装Python 3.7或更高版本。
    pause
    exit /b 1
)

rem 创建虚拟环境（如果不存在）
if not exist "venv" (
    echo 创建Python虚拟环境...
    python -m venv venv
    
    rem 激活虚拟环境
    call venv\Scripts\activate.bat
    
    rem 安装依赖
    echo 安装依赖包...
    pip install -r requirements.txt
    
    if %ERRORLEVEL% neq 0 (
        echo 错误: 安装依赖失败。
        pause
        exit /b 1
    )
) else (
    rem 激活虚拟环境
    call venv\Scripts\activate.bat
)

rem 启动分析器
echo 启动分析器服务...
python mahjong_analyzer.py --bridge-port 8765 --game-port 8899

rem 检查启动是否成功
if %ERRORLEVEL% neq 0 (
    echo 错误: 启动分析器失败。
    pause
    exit /b 1
)

pause