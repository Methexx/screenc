@echo off
setlocal

REM Always run from this folder so paths work when double-clicked
cd /d "%~dp0"

title Windows Screen Streamer
echo ========================================
echo    Windows Screen Streamer (FFmpeg)
echo ========================================
echo.

REM Check FFmpeg
echo Checking FFmpeg in PATH...
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

REM Optional: hint ADB presence
where adb >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo WARNING: adb not found in PATH. Install platform-tools or open from Android SDK folder.
    echo.
)

REM Setup ADB reverse
echo Setting up ADB reverse port forwarding...
adb reverse tcp:27183 tcp:27183
echo.

echo Configuration:
echo   Port:       27183
echo   Framerate:  30 fps
echo   Resolution: 1280x720
echo   Bitrate:    3M
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
    -vf "scale=1280x720" ^
    -b:v 3M ^
    -maxrate 3M ^
    -bufsize 1M ^
    -g 60 ^
    -keyint_min 30 ^
    -sc_threshold 0 ^
    -f h264 ^
    -fflags nobuffer ^
    -flags low_delay ^
    "tcp://0.0.0.0:27183?listen=1"

pause
endlocal
