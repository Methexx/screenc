import 'dart:collection';
import 'package:flutter/foundation.dart';

/// Log entry with timestamp, level, and message
class LogEntry {
  final DateTime timestamp;
  final LogLevel level;
  final String tag;
  final String message;

  LogEntry({
    required this.timestamp,
    required this.level,
    required this.tag,
    required this.message,
  });

  String get formattedTime {
    return '${timestamp.hour.toString().padLeft(2, '0')}:'
        '${timestamp.minute.toString().padLeft(2, '0')}:'
        '${timestamp.second.toString().padLeft(2, '0')}.'
        '${timestamp.millisecond.toString().padLeft(3, '0')}';
  }

  @override
  String toString() => '[$formattedTime] [$level] $tag: $message';
}

enum LogLevel { debug, info, warning, error }

/// Singleton log service that collects logs from the app
class LogService extends ChangeNotifier {
  static final LogService _instance = LogService._internal();
  factory LogService() => _instance;
  LogService._internal();

  final Queue<LogEntry> _logs = Queue<LogEntry>();
  static const int maxLogs = 500;

  List<LogEntry> get logs => _logs.toList();

  void _addLog(LogLevel level, String tag, String message) {
    final entry = LogEntry(
      timestamp: DateTime.now(),
      level: level,
      tag: tag,
      message: message,
    );

    _logs.addLast(entry);
    
    // Keep only last maxLogs entries
    while (_logs.length > maxLogs) {
      _logs.removeFirst();
    }

    // Also print to console for debugging
    if (kDebugMode) {
      print(entry.toString());
    }

    notifyListeners();
  }

  void debug(String tag, String message) => _addLog(LogLevel.debug, tag, message);
  void info(String tag, String message) => _addLog(LogLevel.info, tag, message);
  void warning(String tag, String message) => _addLog(LogLevel.warning, tag, message);
  void error(String tag, String message) => _addLog(LogLevel.error, tag, message);

  void clear() {
    _logs.clear();
    notifyListeners();
  }
}

/// Global log instance for easy access
final appLog = LogService();
