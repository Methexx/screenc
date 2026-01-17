import 'dart:io';
import 'dart:typed_data';
import 'dart:async';

/// Windows H.264 Video Sender
/// Streams dummy H.264 NAL units to test Android receiver

const int SEND_PORT = 27183;
const int FRAME_RATE = 30;
const int FRAME_INTERVAL_MS = (1000 ~/ FRAME_RATE);

// H.264 SPS (Sequence Parameter Set) - 1920x1080
final Uint8List SPS_NAL = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x67, 0x42, 0xc0, 0x1e, 0xd9, 0x00, 0xf0, 0x04, 0x4f, 0xcb, 0x80, 0xb5,
  0x01, 0x01, 0x01, 0x40, 0x00, 0x00, 0x03, 0x00, 0x40, 0x00, 0x00, 0x03,
  0x00, 0xca, 0x3c, 0x48, 0x96, 0x58, 0x04, 0x04, 0x04, 0x08
]);

// H.264 PPS (Picture Parameter Set)
final Uint8List PPS_NAL = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x68, 0xce, 0x3c, 0x80
]);

// Dummy IDR frame (minimal valid H.264 slice)
final Uint8List IDR_NAL = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x65, 0xb8, 0x00, 0x00, 0x03, 0x00, 0x00, 0x03, 0x00, 0x00, 0x03, 0x00,
  0x00, 0x03, 0x00, 0x00, 0x03, 0x00
]);

void main(List<String> args) async {
  print('🎥 Windows H.264 Video Sender');
  print('========================================');
  print('Listening on 0.0.0.0:$SEND_PORT');
  print('Waiting for Android receiver to connect...');
  print('Frame Rate: $FRAME_RATE fps');
  print('\nPress Ctrl+C to stop\n');

  final server = await ServerSocket.bind('0.0.0.0', SEND_PORT);
  
  await for (Socket client in server) {
    print('[${DateTime.now()}] ✓ Client connected: ${client.remoteAddress.address}:${client.remotePort}');
    handleClient(client);
  }
}

void handleClient(Socket client) async {
  try {
    int frameCount = 0;
    bool spsppsSent = false;

    // Send SPS and PPS first
    client.add(SPS_NAL);
    client.add(PPS_NAL);
    spsppsSent = true;
    print('[${DateTime.now()}] 📤 Sent SPS & PPS');

    // Stream frames at specified frame rate
    while (true) {
      // Send IDR frame
      client.add(IDR_NAL);
      frameCount++;

      // Log every 30 frames (1 second at 30 fps)
      if (frameCount % 30 == 0) {
        print('[${DateTime.now()}] 📹 Sent $frameCount frames (${frameCount ~/ 30}s)');
      }

      // Wait for next frame interval
      await Future.delayed(Duration(milliseconds: FRAME_INTERVAL_MS));
    }
  } catch (e) {
    print('[${DateTime.now()}] ❌ Client error: $e');
  } finally {
    print('[${DateTime.now()}] ✗ Client disconnected');
    await client.close();
  }
}
