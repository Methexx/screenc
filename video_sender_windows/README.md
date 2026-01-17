# Windows H.264 Video Sender

This is a Windows-based H.264 video sender that streams video to the Android receiver app over TCP.

## Usage

```bash
dart run bin/video_sender.dart
```

The sender will:
1. Start listening on `0.0.0.0:27183`
2. Wait for the Android receiver to connect
3. Send H.264 SPS & PPS parameters
4. Stream H.264 IDR frames at 30 fps

## Connection

The Android receiver must connect to:
- **Host**: `127.0.0.1` (via ADB reverse) or laptop's WiFi IP
- **Port**: `27183`

## ADB Reverse Setup

```bash
adb reverse tcp:27183 tcp:27183
```

This forwards the Android device's localhost:27183 to Windows localhost:27183.

## Building

To build as an executable:

```bash
dart compile exe bin/video_sender.dart -o video_sender.exe
```

Then run:
```bash
./video_sender.exe
```
