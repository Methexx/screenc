@echo off
title Windows Screen Streamer
echo ========================================
echo    Windows Screen Streamer (FFmpeg)
echo ========================================
echo.

REM Check FFmpeg
where ffmpeg >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo ERROR: FFmpeg not found!
    echo.
    echo Please install FFmpeg:
    echo   1. Download from: https://ffmpeg.org/download.html
    echo   2. Or install via winget: winget install FFmpeg
    echo   3. Or install via choco: choco install ffmpeg
    echo.
    pause
    exit /b 1
)

echo FFmpeg found!
echo.

REM Setup ADB reverse
echo Setting up ADB reverse port forwarding...
adb reverse tcp:27183 tcp:27183
echo.

echo Configuration:
echo   Port:       27183
echo   Framerate:  30 fps
echo   Bitrate:    4M
echo.
echo Starting screen capture...
echo Waiting for Android device to connect...
echo.
echo Press Ctrl+C to stop
echo.

ffmpeg -f gdigrab -framerate 30 -i desktop ^
    -c:v libx264 ^
    -preset ultrafast ^
    -tune zerolatency ^
    -profile:v baseline ^
    -level 3.1 ^
    -pix_fmt yuv420p ^
    -b:v 4M ^
    -maxrate 4M ^
    -bufsize 1M ^
    -g 60 ^
    -keyint_min 30 ^
    -sc_threshold 0 ^
    -f h264 ^
    -fflags nobuffer ^
    -flags low_delay ^
    "tcp://0.0.0.0:27183?listen=1"

pause
