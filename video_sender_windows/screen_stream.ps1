# Windows Screen Streaming Script using FFmpeg
# Captures your screen and streams H.264 over TCP to Android device

$PORT = 27183
$FRAMERATE = 30
# Lower bitrate to reduce data usage at 720p
$BITRATE = "3M"
# Force scaled output resolution to 720p
$RESOLUTION = "1280x720"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Windows Screen Streamer (FFmpeg)" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Check if FFmpeg is installed
$ffmpegPath = Get-Command ffmpeg -ErrorAction SilentlyContinue
if (-not $ffmpegPath) {
    Write-Host "ERROR: FFmpeg not found!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install FFmpeg:" -ForegroundColor Yellow
    Write-Host "  1. Download from: https://ffmpeg.org/download.html" -ForegroundColor White
    Write-Host "  2. Or install via winget: winget install FFmpeg" -ForegroundColor White
    Write-Host "  3. Or install via choco: choco install ffmpeg" -ForegroundColor White
    Write-Host ""
    Read-Host "Press Enter to exit"
    exit 1
}

Write-Host "FFmpeg found: $($ffmpegPath.Source)" -ForegroundColor Green
Write-Host ""

# Setup ADB reverse
Write-Host "Setting up ADB reverse port forwarding..." -ForegroundColor Yellow
adb reverse tcp:$PORT tcp:$PORT
if ($LASTEXITCODE -ne 0) {
    Write-Host "WARNING: ADB reverse failed. Make sure device is connected." -ForegroundColor Red
}

Write-Host ""
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  Port:       $PORT" -ForegroundColor White
Write-Host "  Framerate:  $FRAMERATE fps" -ForegroundColor White
Write-Host "  Bitrate:    $BITRATE" -ForegroundColor White
Write-Host "  Resolution: $RESOLUTION" -ForegroundColor White
Write-Host "  Mode:       Auto-reconnect (persistent server)" -ForegroundColor Green
Write-Host ""
Write-Host "Starting screen capture server..." -ForegroundColor Green
Write-Host "Server will keep running and accept reconnections!" -ForegroundColor Cyan
Write-Host "Waiting for Android device to connect..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Press Ctrl+C to stop" -ForegroundColor Gray
Write-Host ""

# Loop to restart FFmpeg automatically after disconnection
$connectionCount = 0
while ($true) {
    $connectionCount++
    Write-Host "[$([DateTime]::Now.ToString('HH:mm:ss'))] Ready for connection #$connectionCount..." -ForegroundColor Yellow
    
    # Stream screen using FFmpeg
    # -f gdigrab: Windows screen capture
    # -framerate: Capture frame rate
    # -i desktop: Capture entire desktop
    # -c:v libx264: H.264 encoder
    # -preset ultrafast: Lowest latency
    # -tune zerolatency: Optimize for streaming
    # -profile:v baseline: Most compatible H.264 profile
    # -level 3.1: H.264 level for mobile devices
    # -pix_fmt yuv420p: Standard pixel format
    # -g: GOP size (keyframe interval)
    # -f h264: Raw H.264 output
    # tcp://0.0.0.0:PORT?listen=1: TCP server mode
    
    ffmpeg -f gdigrab -framerate $FRAMERATE -i desktop `
        -c:v libx264 `
        -preset ultrafast `
        -tune zerolatency `
        -profile:v baseline `
        -level 3.1 `
        -pix_fmt yuv420p `
        -vf "scale=$RESOLUTION" `
        -b:v $BITRATE `
        -maxrate $BITRATE `
        -bufsize 1M `
        -g $($FRAMERATE * 2) `
        -keyint_min $FRAMERATE `
        -sc_threshold 0 `
        -f h264 `
        -fflags nobuffer `
        -flags low_delay `
        "tcp://0.0.0.0:${PORT}?listen=1" 2>&1 | ForEach-Object {
            # Filter FFmpeg output to reduce noise
            if ($_ -match "frame=|speed=|bitrate=") {
                Write-Host $_ -ForegroundColor Gray
            } elseif ($_ -match "error|Error|ERROR") {
                Write-Host $_ -ForegroundColor Red
            }
        }
    
    # Check if user pressed Ctrl+C
    if ($LASTEXITCODE -eq -1073741510) {
        Write-Host "`nServer stopped by user." -ForegroundColor Yellow
        break
    }
    
    Write-Host "`n[$([DateTime]::Now.ToString('HH:mm:ss'))] Connection #$connectionCount ended. Restarting in 2 seconds..." -ForegroundColor Cyan
    Write-Host "Ready for next Android connection...`n" -ForegroundColor Green
    Start-Sleep -Seconds 2
}
