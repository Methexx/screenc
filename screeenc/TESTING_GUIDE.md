# Testing Guide - Android Receiver

## Quick Start Testing

### 1. Build and Install the App

```bash
# Navigate to project directory
cd screeenc

# Get Flutter dependencies
flutter pub get

# Connect Android device via USB

# Enable USB debugging on Android:
# Settings → About Phone → Tap "Build Number" 7 times
# Settings → Developer Options → Enable "USB Debugging"

# Build and install
flutter run
```

### 2. Set Up ADB Port Forward

```bash
# Forward TCP port 27183 from Windows to Android
adb forward tcp:27183 tcp:27183

# Verify forward is active
adb forward --list
# Should show: emulator-5554 tcp:27183 tcp:27183
```

### 3. Test with FFmpeg (Mock H.264 Stream)

Before building the Windows sender, test with a simulated H.264 stream:

```bash
# Install FFmpeg if not already installed
# Download from: https://ffmpeg.org/download.html

# Generate test pattern and stream to port 27183
ffmpeg -re -f lavfi -i testsrc=size=1920x1080:rate=30 -pix_fmt yuv420p \
  -vcodec libx264 -preset ultrafast -tune zerolatency \
  -profile:v baseline -level 3.1 \
  -x264opts keyint=30:min-keyint=30 \
  -b:v 2M -maxrate 2M -bufsize 4M \
  -f h264 tcp://127.0.0.1:27183?listen=1
```

**Alternative: Stream a video file**
```bash
ffmpeg -re -i input_video.mp4 \
  -vcodec libx264 -preset ultrafast -tune zerolatency \
  -f h264 tcp://127.0.0.1:27183?listen=1
```

### 4. Use the Android App

1. Open "Screen Receiver" app on Android
2. Press **"Start Receiver"** button
3. Watch the status change:
   - "Starting..." → "Connecting to Windows host"
   - "Connected" → "Receiving video stream"
4. Video should render on the Android screen
5. Press **"Stop Receiver"** to disconnect

---

## Monitoring and Debugging

### View Android Logs

```bash
# View all app logs
adb logcat | grep "screeenc"

# View specific component logs
adb logcat | grep "VideoReceiverService"
adb logcat | grep "H264Decoder"
adb logcat | grep "TcpVideoReceiver"
adb logcat | grep "MainActivity"

# Clear logs before testing
adb logcat -c
```

### Check USB Connection

```bash
# List connected devices
adb devices

# Check USB state on Android
adb shell dumpsys battery | grep "USB powered"

# Monitor USB connection events
adb logcat | grep "UsbConnectionReceiver"
```

### Verify Network Connection

```bash
# Check if port 27183 is listening on Windows
netstat -an | findstr 27183

# Test connection from Android side
adb shell netstat | grep 27183
```

### Check MediaCodec Support

```bash
# List hardware video decoders
adb shell pm list features | grep video.decoder

# Check H.264 codec info
adb shell dumpsys media.codec_list | grep -A 20 "OMX.google.h264.decoder"
```

---

## Expected Log Output

### Successful Connection

```
I/VideoReceiverService: Service created
I/VideoReceiverService: Starting video receiver service...
I/TcpVideoReceiver: Connecting to 127.0.0.1:27183...
I/TcpVideoReceiver: Connected successfully
I/H264Decoder: MediaCodec configured: 1920x1080
I/VideoReceiverService: Surface created
D/VideoReceiverService: Decoded 60 frames
D/VideoReceiverService: Decoded 120 frames
```

### USB Connection

```
I/UsbConnectionReceiver: USB power detected - starting receiver service
I/VideoReceiverService: Starting video receiver service...
```

### Disconnection

```
I/UsbConnectionReceiver: USB disconnected - stopping receiver service
I/VideoReceiverService: Stopping video receiver service...
I/TcpVideoReceiver: Disconnected
I/H264Decoder: Decoder released
I/VideoReceiverService: Service destroyed
```

---

## Common Issues and Solutions

### Issue: App crashes on startup
**Solution:**
- Check Android version (minimum SDK 26)
- Verify all permissions granted
- Check logcat for crash stack trace

### Issue: "Connection failed" error
**Solution:**
```bash
# Restart ADB forward
adb forward --remove-all
adb forward tcp:27183 tcp:27183

# Check if port is in use
netstat -ano | findstr 27183

# Test with telnet
telnet 127.0.0.1 27183
```

### Issue: Video not rendering
**Solution:**
- Check if H.264 stream is valid
- Verify MediaCodec configuration in logs
- Look for "Surface created" message
- Check device supports H.264 hardware decoding

### Issue: Service not auto-starting on USB
**Solution:**
- Check USB debugging is enabled
- Verify BroadcastReceiver in manifest
- Check logs: `adb logcat | grep "UsbConnectionReceiver"`
- Manually test: Press "Start Receiver" in app

### Issue: Permission denied errors
**Solution:**
```bash
# Grant notification permission manually
adb shell pm grant com.example.screeenc android.permission.POST_NOTIFICATIONS

# Check permissions
adb shell dumpsys package com.example.screeenc | grep permission
```

---

## Performance Benchmarks

### Expected Performance
- **Latency**: 50-150ms end-to-end
- **Frame Rate**: 30-60 FPS (depends on stream)
- **Resolution**: 1920x1080 (configurable)
- **CPU Usage**: 5-15% (hardware decoding)
- **Memory**: ~100-200 MB

### Monitor Performance

```bash
# CPU usage
adb shell top | grep screeenc

# Memory usage
adb shell dumpsys meminfo com.example.screeenc

# GPU usage (if available)
adb shell dumpsys gfxinfo com.example.screeenc
```

---

## Testing Checklist

- [ ] App installs successfully
- [ ] Permissions granted (notifications)
- [ ] ADB port forward configured
- [ ] App shows "Idle" status on launch
- [ ] "Start Receiver" button works
- [ ] Status changes to "Connecting..."
- [ ] Status changes to "Connected" when stream available
- [ ] Video renders smoothly
- [ ] Frame count increases
- [ ] "Stop Receiver" button works
- [ ] Status changes to "Stopped"
- [ ] USB disconnect stops service
- [ ] USB reconnect auto-starts service (if configured)
- [ ] Notification shows during streaming
- [ ] App survives screen rotation
- [ ] App works after backgrounding

---

## Test Video Files

Download sample H.264 test videos:
- [Big Buck Bunny](http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4)
- [Sintel](http://commondatastorage.googleapis.com/gtv-videos-bucket/sample/Sintel.mp4)

Stream with FFmpeg:
```bash
ffmpeg -re -i BigBuckBunny.mp4 -vcodec copy -f h264 tcp://127.0.0.1:27183?listen=1
```

---

## Next: Windows Sender Testing

Once Android receiver is verified, proceed to build and test Windows sender:
1. Implement DXGI screen capture
2. Implement H.264 encoder
3. Implement TCP server on port 27183
4. Test full Windows → USB → Android pipeline

---

## Support

For issues or questions:
1. Check logcat output
2. Verify ADB connection
3. Test with FFmpeg first
4. Check device compatibility
5. Review IMPLEMENTATION_STATUS.md
