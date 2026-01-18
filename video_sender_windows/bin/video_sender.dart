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
  print('📡 Server will keep running and accept reconnections');
  print('\nPress Ctrl+C to stop\n');

  final server = await ServerSocket.bind(
    '0.0.0.0', 
    SEND_PORT,
    shared: true, // Allow port reuse
  );
  
  int connectionCount = 0;
  
  // Keep accepting connections forever
  await for (Socket client in server) {
    connectionCount++;
    print('[${DateTime.now()}] ✓ Client #$connectionCount connected: ${client.remoteAddress.address}:${client.remotePort}');
    
    // Handle each client in isolation without blocking new connections
    handleClient(client, connectionCount);
  }
}

void handleClient(Socket client, int connectionId) async {
  try {
    int frameCount = 0;
    bool isConnected = true;

    // Listen for disconnection
    client.listen(
      (data) {
        // Client might send data, just ignore it
      },
      onDone: () {
        isConnected = false;
        print('[${DateTime.now()}] ℹ️  Client #$connectionId closed connection gracefully');
      },
      onError: (error) {
        isConnected = false;
        print('[${DateTime.now()}] ⚠️  Client #$connectionId error: $error');
      },
      cancelOnError: true,
    );

    // Send SPS and PPS first
    client.add(SPS_NAL);
    client.add(PPS_NAL);
    await client.flush();
    print('[${DateTime.now()}] 📤 Sent SPS & PPS to client #$connectionId');

    // Stream frames at specified frame rate
    while (isConnected) {
      try {
        // Send IDR frame
        client.add(IDR_NAL);
        frameCount++;

        // Log every 30 frames (1 second at 30 fps)
        if (frameCount % 30 == 0) {
          print('[${DateTime.now()}] 📹 Client #$connectionId: Sent $frameCount frames (${frameCount ~/ 30}s)');
        }

        // Flush to ensure data is sent
        await client.flush();

        // Wait for next frame interval
        await Future.delayed(Duration(milliseconds: FRAME_INTERVAL_MS));
      } catch (e) {
        // Socket closed or error during write
        isConnected = false;
        break;
      }
    }
  } catch (e) {
    print('[${DateTime.now()}] ❌ Client #$connectionId unexpected error: $e');
  } finally {
    print('[${DateTime.now()}] ✗ Client #$connectionId disconnected (Total frames sent: ${0})');
    print('[${DateTime.now()}] 🔄 Ready for next connection...\n');
    
    try {
      await client.close();
    } catch (e) {
      // Ignore close errors
    }
  }
}
