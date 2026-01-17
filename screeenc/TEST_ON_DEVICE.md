# Testing on Physical Device

## Prerequisites
- Physical Android device (not emulator)
- USB cable to connect device to laptop
- Python 3 installed on laptop
- ADB (Android Debug Bridge) installed

## Steps to Test

### 1. Connect Your Android Device
```bash
# Enable USB debugging on your Android device (Settings > Developer Options > USB Debugging)

# Connect device via USB and verify
adb devices
# You should see your device listed
```

### 2. Set Up Port Forwarding
```bash
# Forward TCP port 27183 from device to laptop
adb reverse tcp:27183 tcp:27183

# Verify the reverse port forwarding
adb reverse --list
```

### 3. Start the Test TCP Server on Laptop
Open a terminal/PowerShell and run:
```bash
python test_tcp_server.py
```

You should see:
```
✓ TCP Server listening on 127.0.0.1:27183
Waiting for Android device to connect...
Make sure to run: adb reverse tcp:27183 tcp:27183
```

### 4. Install and Run the Android App
In another terminal:
```bash
# Build and install the app
flutter run

# Or if already running, just click "Start Receiver" in the app
```

### 5. Test the Connection
1. Open the app on your device
2. Click "Start Receiver" button
3. You should see:
   - On laptop: "✓ Connected from 127.0.0.1:XXXXX"
   - On device: Notification changes to "Streaming from Windows"
   - On laptop: "Sent frame X" messages

### 6. Stop the Test
- On device: Click "Stop Receiver" in the app
- On laptop: Press `Ctrl+C` to stop the server

## Troubleshooting

### Device Not Found
```bash
# Check if device is connected
adb devices

# Restart ADB
adb kill-server
adb start-server
```

### Connection Failed
```bash
# Clear and re-setup port forwarding
adb reverse --remove tcp:27183
adb reverse tcp:27183 tcp:27183
```

### Python Not Found
Install Python from: https://www.python.org/downloads/

Or use Windows Store: `winget install Python.Python.3`

## What to Expect

**Successful Connection:**
- Server shows: "✓ Connected from 127.0.0.1:XXXXX"
- Device notification: "Streaming from Windows"
- Server sends test H.264 frames at 30 FPS
- Frames are decoded on device (may not show video yet, but no crashes)

**Failed Connection:**
- Device notification: "Disconnected"
- Server keeps waiting for connection
- Check ADB reverse port forwarding

## Next Steps

Once this works, you'll know:
- ✓ TCP connection works over USB
- ✓ H.264 data is being received
- ✓ Decoder is processing frames
- ✓ Service runs without crashing

Then you can proceed to build the actual Windows sender application.
