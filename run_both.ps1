#!/usr/bin/env pwsh
# Run both Android Receiver and Windows Sender apps

$screeencPath = "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\screeenc"
$senderPath = "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\video_sender_windows"

Write-Host "🎬 Starting Video Streaming Test Setup" -ForegroundColor Cyan
Write-Host "=======================================" -ForegroundColor Cyan

# Check ADB
Write-Host "`n📱 Checking device..." -ForegroundColor Yellow
$devices = adb devices | Select-String "device$"
if ($null -eq $devices) {
    Write-Host "❌ No Android device found!" -ForegroundColor Red
    exit 1
}
Write-Host "✓ Device connected" -ForegroundColor Green

# Setup ADB reverse
Write-Host "`n🔗 Setting up ADB reverse..." -ForegroundColor Yellow
adb reverse --remove-all 2>$null
adb reverse tcp:27183 tcp:27183
Write-Host "✓ ADB reverse configured: tcp:27183 <-> tcp:27183" -ForegroundColor Green

# Start Windows sender
Write-Host "`n🖥️  Starting Windows Sender..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$senderPath'; dart run bin/video_sender.dart" -WindowStyle Normal

Start-Sleep -Seconds 2

# Start Android app
Write-Host "`n📲 Starting Android Receiver..." -ForegroundColor Yellow
Push-Location $screeencPath
adb shell am force-stop com.example.screeenc
adb shell am start -n com.example.screeenc/.MainActivity
Pop-Location

Write-Host "`n✓ Setup complete!" -ForegroundColor Green
Write-Host "`n📋 Next steps:" -ForegroundColor Cyan
Write-Host "1. Windows Sender should be streaming on port 27183"
Write-Host "2. Android Receiver app is running"
Write-Host "3. Click 'Start Receiver' in the Android app"
Write-Host "4. Video stream should start flowing"
Write-Host "`nPress any key to continue monitoring logs..."
$null = $Host.UI.RawUI.ReadKey("NoEcho,IncludeKeyDown")

# Monitor logs
Write-Host "`n📊 Monitoring Android logs..." -ForegroundColor Yellow
Push-Location $screeencPath
& ".\view_logs.ps1"
Pop-Location
