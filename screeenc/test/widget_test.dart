// Build & Integration Tests for Screen Receiver App
// Tests app initialization, widget building, and platform channels

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter/services.dart';

import 'package:screeenc/main.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  group('Build Tests', () {
    test('App initializes without errors', () {
      expect(() => const ScreenReceiverApp(), returnsNormally);
    });

    testWidgets('Main app widget builds successfully', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      expect(find.byType(ScreenReceiverApp), findsOneWidget);
    });

    testWidgets('ReceiverScreen builds successfully', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();
      
      expect(find.byType(ReceiverScreen), findsOneWidget);
      expect(find.text('Screen Receiver'), findsOneWidget);
    });

    testWidgets('UI elements are present', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      // Check for start/stop buttons
      expect(find.text('Start Receiver'), findsOneWidget);
      expect(find.text('Stop Receiver'), findsOneWidget);
      
      // Check for status display
      expect(find.text('Idle'), findsOneWidget);
      
      // Check for connection details card
      expect(find.text('Connection Details'), findsOneWidget);
      expect(find.text('27183 (TCP)'), findsOneWidget);
      expect(find.text('H.264 (AVC)'), findsOneWidget);
    });
  });

  group('Platform Channel Tests', () {
    const MethodChannel methodChannel = MethodChannel('com.example.screeenc/video_receiver');

    setUp(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(methodChannel, (MethodCall methodCall) async {
        switch (methodCall.method) {
          case 'startReceiver':
            return true;
          case 'stopReceiver':
            return true;
          case 'getStatus':
            return {
              'isRunning': false,
              'timestamp': DateTime.now().millisecondsSinceEpoch,
            };
          default:
            return null;
        }
      });
    });

    tearDown(() {
      TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
          .setMockMethodCallHandler(methodChannel, null);
    });

    test('Method channel is correctly configured', () async {
      final result = await methodChannel.invokeMethod('getStatus');
      expect(result, isNotNull);
      expect(result['isRunning'], isFalse);
    });

    test('startReceiver method call succeeds', () async {
      final result = await methodChannel.invokeMethod('startReceiver');
      expect(result, true);
    });

    test('stopReceiver method call succeeds', () async {
      final result = await methodChannel.invokeMethod('stopReceiver');
      expect(result, true);
    });
  });

  group('Widget Interaction Tests', () {
    testWidgets('Start button can be tapped', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      final startButton = find.text('Start Receiver');
      expect(startButton, findsOneWidget);
      
      // Button should be enabled initially
      final elevatedButton = tester.widget<ElevatedButton>(
        find.ancestor(
          of: startButton,
          matching: find.byType(ElevatedButton),
        ).first,
      );
      expect(elevatedButton.onPressed, isNotNull);
    });

    testWidgets('Stop button is initially disabled', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      final stopButton = find.text('Stop Receiver');
      expect(stopButton, findsOneWidget);
      
      // Button should be disabled initially (no streaming)
      final elevatedButton = tester.widget<ElevatedButton>(
        find.ancestor(
          of: stopButton,
          matching: find.byType(ElevatedButton),
        ).first,
      );
      expect(elevatedButton.onPressed, isNull);
    });
  });

  group('State Management Tests', () {
    testWidgets('Initial state is correct', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      // Check initial status
      expect(find.text('Idle'), findsOneWidget);
      expect(find.text('Waiting for USB connection...'), findsOneWidget);
      expect(find.text('Not Connected'), findsOneWidget);
    });

    testWidgets('App survives rebuild', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      // Force rebuild
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      expect(find.byType(ReceiverScreen), findsOneWidget);
    });
  });

  group('Instructions Card Tests', () {
    testWidgets('Instructions are displayed', (WidgetTester tester) async {
      await tester.pumpWidget(const ScreenReceiverApp());
      await tester.pumpAndSettle();

      expect(find.text('Instructions'), findsOneWidget);
      expect(find.textContaining('Connect your Android device'), findsOneWidget);
      expect(find.textContaining('Enable USB debugging'), findsOneWidget);
      expect(find.textContaining('adb forward'), findsOneWidget);
    });
  });
}

