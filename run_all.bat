@echo off

REM Start Python Analyzer
echo Starting Python Analyzer...
start cmd.exe /c "cd /d d:\WorkSpace\Saki\mahjong_core && start_analyzer.bat"

REM Wait for Python service to start
echo Waiting for Python service...
ping 127.0.0.1 -n 3 > nul

REM Start Kotlin Desktop App
echo Starting Kotlin Desktop App...
pushd d:\WorkSpace\Saki\compose-mpp
gradlew.bat run
popd

pause