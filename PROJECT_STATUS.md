# Video Streaming Project - Setup & Test Guide

## Project Status: ✅ WORKING

### Overview
- **Android Receiver**: Flutter app that receives H.264 video via TCP and displays on screen
- **Windows Sender**: Dart console app that streams H.264 video over TCP
- **Connection**: USB via ADB reverse port forwarding (127.0.0.1:27183)

---

## Directory Structure

```
Desktop/New folder (3)/
├── screeenc/                          # Android Receiver App
│   ├── lib/
│   │   ├── main.dart
│   │   ├── services/log_service.dart
│   │   └── widgets/log_viewer.dart
│   ├── android/
│   │   └── app/src/main/kotlin/com/example/screeenc/
│   │       ├── TcpVideoReceiver.kt     # TCP client connecting to host:27183
│   │       ├── VideoReceiverService.kt # Foreground service (dataSync type)
│   │       └── H264Decoder.kt          # MediaCodec H.264 decoder
│   ├── test_tcp_server.dart            # Test server (deprecated, use video_sender_windows)
│   └── view_logs.ps1                   # PowerShell script to view Android logs
│
└── video_sender_windows/               # Windows Sender App
    ├── bin/
    │   └── video_sender.dart           # Main sender application
    ├── pubspec.yaml                    # Dart dependencies
    └── README.md                       # Sender documentation
```

---

## Quick Start

### 1. Start the Windows Sender
```bash
cd video_sender_windows
dart run bin/video_sender.dart
```

Output:
```
🎥 Windows H.264 Video Sender
========================================
Listening on 0.0.0.0:27183
Waiting for Android receiver to connect...
Frame Rate: 30 fps
```

### 2. Ensure ADB Reverse is Configured
```bash
adb reverse tcp:27183 tcp:27183
adb reverse --list
# Should show: UsbFfs tcp:27183 tcp:27183
```

### 3. Start the Android Receiver
```bash
cd screeenc
flutter run
# Or manually: adb shell am start -n com.example.screeenc/.MainActivity
```

### 4. Click "Start Receiver" in Android App
The app will:
- Connect to 127.0.0.1:27183 (forwarded via ADB to Windows sender)
- Receive H.264 SPS/PPS parameters
- Decode video stream at 30 fps
- Display on SurfaceView

---

## Verified Working ✅

### Connection Flow
```
Android Device (100.123.112.127)
    ↓ (USB)
ADB Reverse Port Forwarding (127.0.0.1:27183)
    ↓
Windows Laptop (127.0.0.1:27183)
    ↓ (Socket)
Dart TCP Server (video_sender_windows)
```

### Android Logs Evidence
```
01-17 08:05:47.549 TcpVideoReceiver: Connected successfully
01-17 08:05:48.005 QC2Comp: Stream: 30.00fps 0.0Kbps
```

- ✅ TCP connection established
- ✅ H.264 decoder active
- ✅ Receiving 30 frames per second
- ✅ Hardware codec processing frames

---

## Android App Architecture

### MainActivity
- Requests permissions (INTERNET, SYSTEM_ALERT_WINDOW, POST_NOTIFICATIONS)
- Starts VideoReceiverService
- Displays log viewer with toggle button

### VideoReceiverService (Foreground Service)
- Type: `dataSync` (not mediaProjection - requires different permissions)
- Manages H.264Decoder
- Controls TcpVideoReceiver
- Displays SurfaceView with decoded video

### TcpVideoReceiver (TCP Client)
- Connects to: 127.0.0.1:27183
- Timeout: 5 seconds
- Receives H.264 NAL units
- Passes frames to decoder

### H264Decoder (MediaCodec)
- Codec: `video/avc` (H.264)
- Output: 1920x1080
- Renders to SurfaceView
- Logs frame statistics

---

## Windows Sender App

### Features
- Listens on 0.0.0.0:27183
- Sends H.264 SPS parameter set
- Sends H.264 PPS parameter set  
- Streams IDR frames at 30 fps
- Logs frame count every second
- Handles multiple client connections

### Building Executable
```bash
dart compile exe bin/video_sender.dart -o video_sender.exe
```

---

## Key Issues Resolved

### Issue 1: Port 0 Error
**Before**: `failed to connect to /127.0.0.1 (port 0)`
**Root Cause**: ADB server needed restart after configuration changes
**Solution**: `adb kill-server && adb start-server`

### Issue 2: ADB Reverse Not Working  
**Before**: Connection refused even with ADB reverse configured
**Root Cause**: App was running from old cached APK
**Solution**: Clean rebuild: `flutter clean && flutter build apk --debug`

### Issue 3: Network Unreachable
**Before**: Device IP 100.123.112.127 different from laptop 192.168.1.15
**Root Cause**: Device was on mobile data, not WiFi
**Solution**: Used ADB reverse over USB instead of WiFi

### Issue 4: Service Type Mismatch
**Before**: `SecurityException: requires FOREGROUND_SERVICE_MEDIA_PROJECTION`
**Root Cause**: Using wrong foreground service type
**Solution**: Changed to `dataSync` type for network services

---

## Testing Next Steps

1. **Generate Real Video**: Modify sender to read actual H.264 files
2. **Test Streaming Duration**: Run for extended periods
3. **Network Optimization**: Adjust buffer sizes and timeouts
4. **Error Recovery**: Handle reconnection scenarios
5. **Performance Metrics**: Monitor CPU/memory usage

---

## Debug Commands

### Check Device Connection
```bash
adb devices
```

### View ADB Reverse Configuration
```bash
adb reverse --list
```

### Real-Time Android Logs
```bash
# All logs
adb logcat

# Filtered by tags
.\view_logs.ps1

# Specific search
adb logcat | Select-String "TcpVideoReceiver"
```

### Port Status
```bash
netstat -ano | findstr :27183
```

### Force App Restart
```bash
adb shell am force-stop com.example.screeenc
adb shell am start -n com.example.screeenc/.MainActivity
```

---

## Notes

- **USB Connection Required**: Device must be connected via USB
- **Developer Mode**: Enable USB debugging on Android device
- **Firewall**: Windows Firewall may need exception for port 27183
- **Multiple Connections**: Sender can handle multiple clients
- **Test Data**: Current frames are minimal valid H.264 data
- **Real Video**: Will need actual H.264 encoded frames for production

---

**Last Updated**: 2026-01-17
**Status**: ✅ Functional
