#!/usr/bin/env pwsh
# Real-time Android app log viewer
# Filters and displays logs from the screeenc app

param(
    [switch]$Clear,
    [switch]$Verbose
)

Write-Host "Starting log viewer for screeenc app..." -ForegroundColor Green
Write-Host "Press Ctrl+C to stop`n" -ForegroundColor Yellow

if ($Clear) {
    Write-Host "Clearing logcat buffer..." -ForegroundColor Cyan
    adb logcat -c
    Start-Sleep -Milliseconds 500
}

# Define log tags to filter
$tags = @(
    "MainActivity",
    "VideoReceiverService",
    "TcpVideoReceiver",
    "H264Decoder",
    "UsbConnectionReceiver"
)

# Build filter expression
$filter = $tags | ForEach-Object { "${_}:D" }
$filter += "*:S"  # Suppress all other logs

if ($Verbose) {
    # Show all app logs in verbose mode
    adb logcat | Select-String -Pattern "screeenc" -Context 0,0
} else {
    # Filtered view
    adb logcat $filter
}
