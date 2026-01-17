import 'dart:io';
import 'dart:typed_data';

/// Simple TCP Server to test Android Video Receiver
/// Sends dummy H.264 NAL units to simulate video streaming

const String host = '127.0.0.1';
const int port = 27183;

// H.264 NAL start code
final Uint8List nalStartCode = Uint8List.fromList([0x00, 0x00, 0x00, 0x01]);

// Sample SPS (Sequence Parameter Set)
final Uint8List spsNal = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x67, 0x42, 0xc0, 0x1e, 0xd9, 0x00, 0xf0, 0x04, 0x4f, 0xcb, 0x80, 0xb5, 0x01, 0x01, 0x01, 0x40
]);

// Sample PPS (Picture Parameter Set)
final Uint8List ppsNal = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x68, 0xce, 0x3c, 0x80
]);

// Sample IDR frame header (I-frame)
final Uint8List idrNal = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x65, 0x88, 0x84, 0x00, 0x33, 0xff
]);

// Sample P-frame
final Uint8List pNal = Uint8List.fromList([
  0x00, 0x00, 0x00, 0x01, // Start code
  0x41, 0x9a, 0x21, 0x8c, 0x48
]);

Future<void> sendTestStream(Socket client) async {
  print('Sending test H.264 stream...');
  int frameCount = 0;

  try {
    // Send SPS and PPS first
    client.add(spsNal);
    await Future.delayed(Duration(milliseconds: 10));
    client.add(ppsNal);
    await Future.delayed(Duration(milliseconds: 10));

    // Send frames at ~30 FPS
    while (true) {
      // Send IDR frame every 30 frames
      if (frameCount % 30 == 0) {
        client.add(idrNal);
        print('Sent frame $frameCount (I-frame)');
      } else {
        client.add(pNal);
        if (frameCount % 10 == 0) {
          print('Sent frame $frameCount');
        }
      }

      frameCount++;
      await Future.delayed(Duration(milliseconds: 33)); // ~30 FPS
    }
  } catch (e) {
    print('\nClient disconnected: $e');
  }
}

void main() async {
  try {
    final server = await ServerSocket.bind(host, port);
    print('✓ TCP Server listening on $host:$port');
    print('\nWaiting for Android device to connect...');
    print('Make sure to run: adb reverse tcp:27183 tcp:27183');
    print('\nPress Ctrl+C to stop\n');

    await for (final client in server) {
      print('\n✓ Connected from ${client.remoteAddress.address}:${client.remotePort}');

      try {
        await sendTestStream(client);
      } catch (e) {
        print('Error: $e');
      } finally {
        await client.close();
        print('Connection closed\n');
        print('Waiting for next connection...');
      }
    }
  } catch (e) {
    print('Server error: $e');
    exit(1);
  }
}
