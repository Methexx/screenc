# Screeenc - USB-Only Screen Sharing (Windows → Android)

A USB-only screen sharing solution that captures Windows desktop screen and streams it to an Android device using **only a USB cable**. No Wi-Fi, no internet, no web technologies.

## 🎯 Project Goal

Stream live Windows desktop screen to Android device through USB connection with minimal latency, using officially supported APIs without kernel-level access or security bypasses.

--- 

## 📋 Project Status

### ✅ Android Receiver - **COMPLETE**
The Android receiver application is fully implemented and ready for testing.

**Features:**
- ✅ H.264 hardware-accelerated video decoding (MediaCodec)
- ✅ TCP video stream receiver over USB tunnel
- ✅ Foreground service with notification
- ✅ Auto USB connection/disconnection detection
- ✅ Flutter UI with real-time status updates
- ✅ Platform channels for native-Flutter communication

**Files Created:**
- [android/app/src/main/kotlin/.../VideoReceiverService.kt](android/app/src/main/kotlin/com/example/screeenc/VideoReceiverService.kt)
- [android/app/src/main/kotlin/.../H264Decoder.kt](android/app/src/main/kotlin/com/example/screeenc/H264Decoder.kt)
- [android/app/src/main/kotlin/.../TcpVideoReceiver.kt](android/app/src/main/kotlin/com/example/screeenc/TcpVideoReceiver.kt)
- [android/app/src/main/kotlin/.../UsbConnectionReceiver.kt](android/app/src/main/kotlin/com/example/screeenc/UsbConnectionReceiver.kt)
- [android/app/src/main/kotlin/.../MainActivity.kt](android/app/src/main/kotlin/com/example/screeenc/MainActivity.kt)
- [lib/main.dart](lib/main.dart)

### 🔄 Windows Sender - **PENDING**
Next phase: Build Windows desktop application for screen capture and H.264 encoding.

**Planned Features:**
- DXGI Desktop Duplication API for screen capture
- Windows Media Foundation for H.264 encoding
- TCP server on localhost:27183
- USB transport via ADB port forward

---

## 🏗️ Architecture

```
┌─────────────────────┐
│   Windows Desktop   │
│  (Screen Capture)   │
└──────────┬──────────┘
           │ DXGI API
           ▼
┌─────────────────────┐
│   H.264 Encoder     │
│  (Media Foundation) │
└──────────┬──────────┘
           │ TCP Stream
           ▼
┌─────────────────────┐
│  TCP Server :27183  │
└──────────┬──────────┘
           │
           │ USB Cable
           │ (ADB Forward)
           ▼
┌─────────────────────┐
│ Android TCP Client  │
│    (localhost)      │
└──────────┬──────────┘
           │ H.264 Stream
           ▼
┌─────────────────────┐
│  MediaCodec Decoder │
│  (Hardware Accel)   │
└──────────┬──────────┘
           │ Decoded Frames
           ▼
┌─────────────────────┐
│   SurfaceView       │
│   (Android Screen)  │
└─────────────────────┘
```

---

## 🚀 Quick Start

### Prerequisites
- **Android Device**: Android 8.0+ (API 26+) with USB debugging enabled
- **Windows PC**: Windows 10/11 with ADB installed
- **USB Cable**: Data-capable USB cable
- **Flutter**: Flutter 3.10.1 or higher (for development)

### 1. Install Android App

```bash
# Clone the repository
git clone <repository-url>
cd screeenc

# Get Flutter dependencies
flutter pub get

# Connect Android device via USB

# Build and install
flutter run
```

### 2. Set Up USB Port Forward

```bash
# Forward TCP port from Windows to Android
adb forward tcp:27183 tcp:27183

# Verify
adb forward --list
```

### 3. Test with Mock Stream (Optional)

Test the receiver before building Windows sender:

```bash
# Install FFmpeg: https://ffmpeg.org/download.html

# Stream test pattern
ffmpeg -re -f lavfi -i testsrc=size=1920x1080:rate=30 -pix_fmt yuv420p \
  -vcodec libx264 -preset ultrafast -tune zerolatency \
  -f h264 tcp://127.0.0.1:27183?listen=1
```

### 4. Use the App

1. Open "Screen Receiver" app on Android
2. Press **"Start Receiver"**
3. Status should change to "Connected" when stream is available
4. Video will render on Android screen

---

## 📚 Documentation

- **[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)** - Detailed implementation overview
- **[TESTING_GUIDE.md](TESTING_GUIDE.md)** - Comprehensive testing instructions
- **[android-receiver/README.md](android-receiver/README.md)** - Android receiver technical details
- **[windows-sender/README.md](windows-sender/README.md)** - Windows sender technical details

---

## 🛠️ Technology Stack

### Android Receiver
| Component | Technology |
|-----------|-----------|
| UI Framework | Flutter (Material 3) |
| Native Code | Kotlin |
| Video Decoding | MediaCodec (H.264/AVC) |
| Rendering | SurfaceView |
| Data Transport | TCP Socket |
| USB Detection | BroadcastReceiver |
| Background Service | Foreground Service |

### Windows Sender (Planned)
| Component | Technology |
|-----------|-----------|
| Language | C++ or C# |
| Screen Capture | DXGI Desktop Duplication API |
| Video Encoding | Windows Media Foundation |
| Data Transport | TCP Server (Winsock) |
| USB Communication | ADB Port Forward |

---

## 🔒 Security & Compliance

- ✅ **User-mode only** - No kernel drivers or system hooks
- ✅ **Official APIs** - Uses Microsoft and Google supported APIs
- ✅ **No DRM bypass** - Cannot capture protected content
- ✅ **No input interception** - Screen capture only, no control
- ✅ **USB-only** - No network exposure or internet dependency

### Limitations
- Cannot capture DRM-protected or secure surfaces
- Requires USB debugging enabled on Android
- Windows application must be running to stream

---

## 🧪 Testing

See [TESTING_GUIDE.md](TESTING_GUIDE.md) for comprehensive testing instructions.

**Quick Test:**
```bash
# 1. Install app
flutter run

# 2. Forward port
adb forward tcp:27183 tcp:27183

# 3. Start mock stream
ffmpeg -re -f lavfi -i testsrc=size=1920x1080:rate=30 \
  -vcodec libx264 -preset ultrafast \
  -f h264 tcp://127.0.0.1:27183?listen=1

# 4. Press "Start Receiver" in app
```

### Monitoring Logs
```bash
# View all app logs
adb logcat | grep "screeenc"

# View specific components
adb logcat | grep "VideoReceiverService\|H264Decoder\|TcpVideoReceiver"
```

---

## 📱 System Requirements

### Android Device
- **OS**: Android 8.0 (Oreo) or higher (API 26+)
- **Hardware**: H.264 hardware decoder support (most modern devices)
- **USB**: USB debugging capability
- **Permissions**: Notification permission (Android 13+)

### Windows PC
- **OS**: Windows 10/11
- **Software**: ADB (Android Debug Bridge)
- **Ports**: TCP port 27183 available
- **Sender App**: (Coming soon)

---

## 🗺️ Roadmap

- [x] **Phase 1**: Android receiver implementation
  - [x] H.264 decoder with MediaCodec
  - [x] TCP receiver
  - [x] Foreground service
  - [x] USB detection
  - [x] Flutter UI
  - [x] Platform channels
- [ ] **Phase 2**: Windows sender implementation
  - [ ] DXGI screen capture
  - [ ] H.264 encoder (Windows Media Foundation)
  - [ ] TCP server
  - [ ] Configuration UI
- [ ] **Phase 3**: Optimization
  - [ ] Latency reduction
  - [ ] Adaptive bitrate
  - [ ] Multiple resolution support
  - [ ] Performance tuning
- [ ] **Phase 4**: Polish
  - [ ] Error handling improvements
  - [ ] Logging and diagnostics
  - [ ] User documentation
  - [ ] Installer packages

---

## 🤝 Contributing

This project is currently in active development. Contributions, issues, and feature requests are welcome!

---

## 📄 License

[Add your license here]

---

## 🙏 Acknowledgments

- Flutter team for excellent cross-platform framework
- Android MediaCodec for hardware-accelerated decoding
- Microsoft DXGI team for desktop duplication API
- FFmpeg for testing utilities

---

## 📞 Support

For issues or questions:
1. Check [TESTING_GUIDE.md](TESTING_GUIDE.md)
2. Review Android logcat output
3. Verify USB/ADB connection
4. Open an issue on GitHub

---

**Ready to stream your Windows screen to Android over USB! 🚀**

