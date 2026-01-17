# Windows H.264 Video Sender

Windows-side tools to stream your desktop as H.264 to the Android receiver over TCP (ADB reverse or LAN).

## Quick Start (recommended)

Requires FFmpeg in PATH (install via `winget install FFmpeg`), and ADB.

```powershell
cd "c:\Users\Methum-PC\OneDrive\Desktop\New folder (3)\video_sender_windows"
./screen_stream.ps1
```

What it does:
1) Sets up `adb reverse tcp:27183 tcp:27183`
2) Captures desktop via FFmpeg (`gdigrab`) at 1920x1080, 30fps
3) Encodes H.264 (baseline, ultrafast, zerolatency) and listens on `0.0.0.0:27183`
4) Waits for the Android app to press **Start Receiver**

On the phone: open the Screeenc app → tap **Start Receiver** → your Windows desktop appears. Use the red **✕ CLOSE** to dismiss.

## Legacy Dart sender (optional)

If you prefer the Dart TCP sender:

```bash
dart run bin/video_sender.dart
```

The sender will:
1. Start listening on `0.0.0.0:27183`
2. Wait for the Android receiver to connect
3. Send H.264 SPS & PPS parameters
4. Stream H.264 IDR frames at 30 fps

## Connection

Android receiver connects to:
- **Host**: `127.0.0.1` (via ADB reverse) or your PC’s LAN IP
- **Port**: `27183`

ADB reverse:

```bash
adb reverse tcp:27183 tcp:27183
```

## Building the Dart sender

```bash
dart compile exe bin/video_sender.dart -o video_sender.exe
./video_sender.exe
```
