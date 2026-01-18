import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';
import 'services/log_service.dart';
import 'widgets/log_viewer.dart';

void main() {
  runApp(const ScreenReceiverApp());
}

class ScreenReceiverApp extends StatelessWidget {
  const ScreenReceiverApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Screen Receiver',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.blue),
        useMaterial3: true,
      ),
      home: const ReceiverScreen(),
    );
  }
}

class ReceiverScreen extends StatefulWidget {
  const ReceiverScreen({super.key});

  @override
  State<ReceiverScreen> createState() => _ReceiverScreenState();
}

class _ReceiverScreenState extends State<ReceiverScreen> {
  static const methodChannel = MethodChannel('com.example.screeenc/video_receiver');
  static const eventChannel = EventChannel('com.example.screeenc/video_status');

  String _status = 'Idle';
  String _statusMessage = 'Waiting for USB connection...';
  bool _isStreaming = false;
  int _frameCount = 0;
  DateTime? _lastUpdate;
  bool _showLogs = false;
  bool _isLandscape = false;

  @override
  void initState() {
    super.initState();
    appLog.info('App', 'Screen Receiver initialized');
    // Set initial portrait orientation
    SystemChrome.setPreferredOrientations([
      DeviceOrientation.portraitUp,
      DeviceOrientation.portraitDown,
    ]);
    _requestPermissions();
    _listenToStatusUpdates();
    _setupOrientationListener();
  }
  
  /// Listen to orientation change broadcasts from the service
  void _setupOrientationListener() {
    methodChannel.setMethodCallHandler((call) async {
      if (call.method == 'setOrientation') {
        final landscape = call.arguments['landscape'] as bool? ?? false;
        await _setOrientation(landscape);
      }
    });
  }

  /// Request necessary permissions
  Future<void> _requestPermissions() async {
    appLog.debug('Permissions', 'Checking notification permission...');
    // Request notification permission for Android 13+
    if (await Permission.notification.isDenied) {
      appLog.info('Permissions', 'Requesting notification permission');
      final result = await Permission.notification.request();
      appLog.info('Permissions', 'Notification permission: $result');
    } else {
      appLog.debug('Permissions', 'Notification permission already granted');
    }
  }

  /// Listen to status updates from native Android code
  void _listenToStatusUpdates() {
    appLog.info('EventChannel', 'Setting up status listener...');
    eventChannel.receiveBroadcastStream().listen(
      (dynamic event) {
        if (event is Map) {
          final status = event['status'] ?? 'Unknown';
          final message = event['message'] ?? '';
          
          appLog.info('Status', '$status: $message');
          
          setState(() {
            _status = status;
            _statusMessage = message;
            _lastUpdate = DateTime.now();

            // Update streaming state based on status
            if (_status.toLowerCase() == 'connected') {
              _isStreaming = true;
              _frameCount++;
            } else if (_status.toLowerCase() == 'disconnected' || _status.toLowerCase() == 'stopped') {
              _isStreaming = false;
            }
          });
        }
      },
      onError: (dynamic error) {
        appLog.error('EventChannel', 'Error: $error');
        setState(() {
          _status = 'Error';
          _statusMessage = error.toString();
        });
      },
    );
    appLog.debug('EventChannel', 'Status listener ready');
  }

  /// Start the video receiver service
  Future<void> _startReceiver() async {
    try {
      appLog.info('Receiver', 'Starting video receiver service...');
      await methodChannel.invokeMethod('startReceiver');
      appLog.info('Receiver', 'Start command sent successfully');
      setState(() {
        _status = 'Starting...';
        _statusMessage = 'Connecting to Windows host';
        _isStreaming = true; // Enable streaming UI immediately
      });
    } on PlatformException catch (e) {
      appLog.error('Receiver', 'Failed to start: ${e.message}');
      setState(() {
        _status = 'Error';
        _statusMessage = 'Failed to start: ${e.message}';
      });
    }
  }

  /// Stop the video receiver service
  Future<void> _stopReceiver() async {
    try {
      appLog.info('Receiver', 'Stopping video receiver service...');
      await methodChannel.invokeMethod('stopReceiver');
      appLog.info('Receiver', 'Stop command sent successfully');
      // Reset orientation to portrait when stopping
      await _setOrientation(false);
      setState(() {
        _status = 'Stopping...';
        _statusMessage = 'Disconnecting';
        _isStreaming = false;
      });
    } on PlatformException catch (e) {
      appLog.error('Receiver', 'Failed to stop: ${e.message}');
      setState(() {
        _status = 'Error';
        _statusMessage = 'Failed to stop: ${e.message}';
      });
    }
  }

  /// Set screen orientation
  Future<void> _setOrientation(bool landscape) async {
    try {
      if (landscape) {
        appLog.info('Orientation', 'Switching to landscape mode');
        await SystemChrome.setPreferredOrientations([
          DeviceOrientation.landscapeLeft,
          DeviceOrientation.landscapeRight,
        ]);
      } else {
        appLog.info('Orientation', 'Switching to portrait mode');
        await SystemChrome.setPreferredOrientations([
          DeviceOrientation.portraitUp,
          DeviceOrientation.portraitDown,
        ]);
      }
      setState(() {
        _isLandscape = landscape;
      });
      appLog.info('Orientation', 'Orientation changed to ${landscape ? "landscape" : "portrait"}');
    } catch (e) {
      appLog.error('Orientation', 'Failed to change orientation: $e');
    }
  }

  Color _getStatusColor() {
    switch (_status.toLowerCase()) {
      case 'connected':
        return Colors.green;
      case 'connecting...':
      case 'starting...':
        return Colors.orange;
      case 'error':
        return Colors.red;
      case 'disconnected':
      case 'stopped':
        return Colors.grey;
      default:
        return Colors.blue;
    }
  }

  IconData _getStatusIcon() {
    switch (_status.toLowerCase()) {
      case 'connected':
        return Icons.cast_connected;
      case 'connecting...':
      case 'starting...':
        return Icons.sync;
      case 'error':
        return Icons.error_outline;
      case 'disconnected':
      case 'stopped':
        return Icons.cast;
      default:
        return Icons.devices;
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        backgroundColor: Theme.of(context).colorScheme.inversePrimary,
        title: const Text('Screen Receiver'),
        actions: [
          // Log viewer toggle
          IconButton(
            icon: Icon(
              _showLogs ? Icons.terminal : Icons.terminal_outlined,
              color: _showLogs ? Colors.green : null,
            ),
            onPressed: () => setState(() => _showLogs = !_showLogs),
            tooltip: 'Toggle logs',
          ),
          Icon(
            _getStatusIcon(),
            color: _getStatusColor(),
          ),
          const SizedBox(width: 16),
        ],
      ),
      body: Container(
        decoration: BoxDecoration(
          gradient: LinearGradient(
            begin: Alignment.topCenter,
            end: Alignment.bottomCenter,
            colors: [
              Theme.of(context).colorScheme.surface,
              Theme.of(context).colorScheme.surfaceContainerHighest,
            ],
          ),
        ),
        child: SafeArea(
          child: _showLogs ? _buildLogView() : _buildMainView(),
        ),
      ),
    );
  }

  Widget _buildLogView() {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        children: [
          // Quick action buttons
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isStreaming ? null : _startReceiver,
                  icon: const Icon(Icons.play_arrow, size: 18),
                  label: const Text('Start'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                  ),
                ),
              ),
              const SizedBox(width: 8),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isStreaming ? _stopReceiver : null,
                  icon: const Icon(Icons.stop, size: 18),
                  label: const Text('Stop'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 8),
          // Status bar
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: _getStatusColor().withOpacity(0.2),
              borderRadius: BorderRadius.circular(8),
              border: Border.all(color: _getStatusColor()),
            ),
            child: Row(
              children: [
                Icon(_getStatusIcon(), color: _getStatusColor(), size: 20),
                const SizedBox(width: 8),
                Text(
                  '$_status: $_statusMessage',
                  style: TextStyle(
                    color: _getStatusColor(),
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          // Log viewer
          const Expanded(child: LogViewer()),
        ],
      ),
    );
  }

  Widget _buildMainView() {
    return SingleChildScrollView(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
          // Status Card
          Card(
            elevation: 8,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
            ),
            child: Padding(
              padding: const EdgeInsets.all(24.0),
              child: Column(
                children: [
                  Icon(
                    _getStatusIcon(),
                    size: 80,
                    color: _getStatusColor(),
                  ),
                  const SizedBox(height: 16),
                  Text(
                    _status,
                    style: Theme.of(context).textTheme.headlineMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                      color: _getStatusColor(),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _statusMessage,
                    textAlign: TextAlign.center,
                    style: Theme.of(context).textTheme.bodyLarge?.copyWith(
                      color: Colors.grey[600],
                    ),
                  ),
                  if (_lastUpdate != null) ...[
                    const SizedBox(height: 8),
                    Text(
                      'Last update: ${_lastUpdate!.toLocal().toString().split('.')[0]}',
                      style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Colors.grey[400],
                      ),
                    ),
                  ],
                ],
              ),
            ),
          ),
          const SizedBox(height: 32),

          // Connection Info
          Card(
            elevation: 4,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    'Connection Details',
                    style: Theme.of(context).textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const Divider(),
                  _buildInfoRow('Status', _isStreaming ? 'Streaming' : 'Not Connected'),
                  _buildInfoRow('Port', '27183 (TCP)'),
                  _buildInfoRow('Transport', 'USB (ADB Forward)'),
                  _buildInfoRow('Codec', 'H.264 (AVC)'),
                  if (_isStreaming)
                    _buildInfoRow('Updates', '$_frameCount'),
                ],
              ),
            ),
          ),
          const SizedBox(height: 24),

          // Control Buttons
          Row(
            children: [
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isStreaming ? null : _startReceiver,
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Start'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.green,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: ElevatedButton.icon(
                  onPressed: _isStreaming ? _stopReceiver : null,
                  icon: const Icon(Icons.stop),
                  label: const Text('Stop'),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.red,
                    foregroundColor: Colors.white,
                    padding: const EdgeInsets.symmetric(vertical: 16),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(12),
                    ),
                  ),
                ),
              ),
            ],
          ),
          
          // Orientation Control Buttons (only visible when streaming)
          if (_isStreaming) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isLandscape ? null : () => _setOrientation(false),
                    icon: const Icon(Icons.stay_current_portrait, size: 20),
                    label: const Text('Portrait'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _isLandscape ? Colors.grey : Colors.blue,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _isLandscape ? null : () => _setOrientation(true),
                    icon: const Icon(Icons.stay_current_landscape, size: 20),
                    label: const Text('Landscape'),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _isLandscape ? Colors.blue : Colors.grey,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(12),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ],
          const SizedBox(height: 16),

          // Instructions
          Card(
            color: Colors.blue[50],
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      Icon(Icons.info_outline, color: Colors.blue[700]),
                      const SizedBox(width: 8),
                      Text(
                        'Instructions',
                        style: TextStyle(
                          fontWeight: FontWeight.bold,
                          color: Colors.blue[700],
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  Text(
                    '1. Connect your Android device to Windows via USB\n'
                    '2. Enable USB debugging on Android\n'
                    '3. Run "adb forward tcp:27183 tcp:27183" on Windows\n'
                    '4. Start the Windows sender application\n'
                    '5. Press "Start Receiver" above',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.blue[900],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
      ),
    );
  }

  Widget _buildInfoRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4.0),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(
            label,
            style: const TextStyle(
              color: Colors.grey,
              fontSize: 14,
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontWeight: FontWeight.w500,
              fontSize: 14,
            ),
          ),
        ],
      ),
    );
  }
}
