# Setup Checklist & Running the System

## Pre-Flight Checks

- [ ] Android device connected via USB
- [ ] Developer mode enabled on device
- [ ] USB debugging enabled
- [ ] `adb devices` shows device as "device" (not offline)
- [ ] Port 27183 is free on Windows (`netstat -ano | findstr :27183`)

## Running the Full System

### Option 1: Automated Setup (Recommended)
```powershell
cd "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)"
.\run_both.ps1
```

### Option 2: Manual Setup

#### Step 1: Kill any existing processes on port 27183
```powershell
$process = (netstat -ano | findstr ":27183").Split()[-1]
taskkill /F /PID $process 2>$null
```

#### Step 2: Configure ADB Reverse
```powershell
adb reverse --remove-all
adb reverse tcp:27183 tcp:27183
adb reverse --list  # Verify output: UsbFfs tcp:27183 tcp:27183
```

#### Step 3: Start Windows Sender (Terminal 1)
```powershell
cd "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\video_sender_windows"
dart run bin/video_sender.dart
```

Expected output:
```
🎥 Windows H.264 Video Sender
========================================
Listening on 0.0.0.0:27183
Waiting for Android receiver to connect...
Frame Rate: 30 fps
Press Ctrl+C to stop
```

#### Step 4: Start Android Receiver (Terminal 2)
```powershell
cd "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\screeenc"
adb shell am force-stop com.example.screeenc
adb shell am start -n com.example.screeenc/.MainActivity
```

Or if already installed:
```powershell
flutter run
```

#### Step 5: Monitor Logs (Terminal 3 - Optional but Recommended)
```powershell
cd "C:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\screeenc"
.\view_logs.ps1
```

#### Step 6: Trigger Connection on Android Device
In the Android app:
1. Tap the "Start Receiver" button
2. Watch the logs for "Connected successfully"
3. Observe video playback on the SurfaceView

## Expected Behavior

### Windows Sender Output
```
✓ Client connected: 127.0.0.1:12345
📤 Sent SPS & PPS
📹 Sent 30 frames (1s)
📹 Sent 60 frames (2s)
```

### Android Logcat Output
```
I TcpVideoReceiver: Connecting to 127.0.0.1:27183...
I TcpVideoReceiver: Connected successfully
I H264Decoder: MediaCodec configured: 1920x1080
W TcpVideoReceiver: Stream ended or connection closed
```

### Android App Display
- Black SurfaceView initially
- Shows notification: "Streaming from Windows" when connected
- Video frames displayed when data received
- Notification updates to "Disconnected" when stream ends

## Troubleshooting

### "Connection refused" Error
**Problem**: TcpVideoReceiver can't connect to 127.0.0.1:27183
**Solutions**:
1. Verify ADB reverse: `adb reverse --list`
2. Restart ADB server: `adb kill-server && adb start-server && adb reverse tcp:27183 tcp:27183`
3. Verify sender is running: `netstat -ano | findstr :27183`
4. Check Windows Firewall (may need to allow port 27183)

### "Port already in use" Error
**Problem**: Port 27183 is already bound
**Solution**:
```powershell
$process = (netstat -ano | findstr ":27183").Split()[-1]
taskkill /F /PID $process
```

### App Crashes on Startup
**Problem**: Service fails to start
**Solution**:
1. Check manifest permissions: `INTERNET`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`
2. Verify foregroundServiceType is `dataSync` (not `mediaProjection`)
3. Rebuild: `flutter clean && flutter build apk --debug && adb install -r build/app/outputs/flutter-apk/app-debug.apk`

### No Connection Event in Sender
**Problem**: Sender doesn't log "Client connected"
**Possible Causes**:
1. ADB reverse not working - check: `adb shell "nc -w 2 127.0.0.1 27183 </dev/null"` (should exit with 0)
2. Android device blocked by firewall
3. Sender crashed silently

### Stream Ends After 2 Seconds
**Problem**: Connection closes shortly after connecting
**Cause**: This is normal with test data - server only sends a few frames
**Solution**: For continuous streaming, will need to send real video data

## Performance Monitoring

### Real-Time Frame Rate
Check Android logcat for QC2Comp statistics:
```bash
adb logcat | findstr /R "Stream: .*fps"
```

### Connection Statistics
Watch sender output for:
```
📹 Sent 30 frames (1s)    # 30 fps
📹 Sent 60 frames (2s)    # 30 fps average
```

## Stopping the System

1. Android app: Press "Stop Receiver" button (if running)
2. Windows sender: Press Ctrl+C in terminal
3. Clean up: `adb reverse --remove-all`

---

**Remember**: Always have the Windows sender running BEFORE clicking "Start Receiver" on the Android app!
