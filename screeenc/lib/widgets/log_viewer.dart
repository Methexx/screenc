import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../services/log_service.dart';

/// In-app log viewer widget
class LogViewer extends StatefulWidget {
  const LogViewer({super.key});

  @override
  State<LogViewer> createState() => _LogViewerState();
}

class _LogViewerState extends State<LogViewer> {
  final ScrollController _scrollController = ScrollController();
  bool _autoScroll = true;
  LogLevel? _filterLevel;

  @override
  void initState() {
    super.initState();
    appLog.addListener(_onLogsChanged);
  }

  @override
  void dispose() {
    appLog.removeListener(_onLogsChanged);
    _scrollController.dispose();
    super.dispose();
  }

  void _onLogsChanged() {
    setState(() {});
    if (_autoScroll) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (_scrollController.hasClients) {
          _scrollController.animateTo(
            _scrollController.position.maxScrollExtent,
            duration: const Duration(milliseconds: 100),
            curve: Curves.easeOut,
          );
        }
      });
    }
  }

  List<LogEntry> get filteredLogs {
    if (_filterLevel == null) return appLog.logs;
    return appLog.logs.where((log) => log.level == _filterLevel).toList();
  }

  Color _getLogColor(LogLevel level) {
    switch (level) {
      case LogLevel.debug:
        return Colors.grey;
      case LogLevel.info:
        return Colors.blue;
      case LogLevel.warning:
        return Colors.orange;
      case LogLevel.error:
        return Colors.red;
    }
  }

  String _getLevelIcon(LogLevel level) {
    switch (level) {
      case LogLevel.debug:
        return '🔍';
      case LogLevel.info:
        return 'ℹ️';
      case LogLevel.warning:
        return '⚠️';
      case LogLevel.error:
        return '❌';
    }
  }

  void _copyLogsToClipboard() {
    final logsText = filteredLogs.map((e) => e.toString()).join('\n');
    Clipboard.setData(ClipboardData(text: logsText));
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Logs copied to clipboard')),
    );
  }

  @override
  Widget build(BuildContext context) {
    final logs = filteredLogs;

    return Container(
      decoration: BoxDecoration(
        color: Colors.black87,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey.shade700),
      ),
      child: Column(
        children: [
          // Header with controls
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.grey.shade900,
              borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
            ),
            child: Row(
              children: [
                const Icon(Icons.terminal, color: Colors.green, size: 18),
                const SizedBox(width: 8),
                Text(
                  'Logs (${logs.length})',
                  style: const TextStyle(
                    color: Colors.white,
                    fontWeight: FontWeight.bold,
                    fontSize: 14,
                  ),
                ),
                const Spacer(),
                // Filter dropdown
                DropdownButton<LogLevel?>(
                  value: _filterLevel,
                  hint: const Text('All', style: TextStyle(color: Colors.white70, fontSize: 12)),
                  dropdownColor: Colors.grey.shade800,
                  underline: const SizedBox(),
                  isDense: true,
                  items: [
                    const DropdownMenuItem(value: null, child: Text('All', style: TextStyle(color: Colors.white))),
                    ...LogLevel.values.map((level) => DropdownMenuItem(
                      value: level,
                      child: Text(
                        level.name.toUpperCase(),
                        style: TextStyle(color: _getLogColor(level)),
                      ),
                    )),
                  ],
                  onChanged: (value) => setState(() => _filterLevel = value),
                ),
                const SizedBox(width: 8),
                // Auto-scroll toggle
                IconButton(
                  icon: Icon(
                    _autoScroll ? Icons.vertical_align_bottom : Icons.vertical_align_center,
                    color: _autoScroll ? Colors.green : Colors.grey,
                    size: 18,
                  ),
                  onPressed: () => setState(() => _autoScroll = !_autoScroll),
                  tooltip: 'Auto-scroll',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                ),
                // Copy button
                IconButton(
                  icon: const Icon(Icons.copy, color: Colors.white70, size: 18),
                  onPressed: _copyLogsToClipboard,
                  tooltip: 'Copy logs',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                ),
                // Clear button
                IconButton(
                  icon: const Icon(Icons.delete_outline, color: Colors.white70, size: 18),
                  onPressed: () => appLog.clear(),
                  tooltip: 'Clear logs',
                  padding: EdgeInsets.zero,
                  constraints: const BoxConstraints(minWidth: 32, minHeight: 32),
                ),
              ],
            ),
          ),
          // Log entries
          Expanded(
            child: logs.isEmpty
                ? const Center(
                    child: Text(
                      'No logs yet...',
                      style: TextStyle(color: Colors.grey),
                    ),
                  )
                : ListView.builder(
                    controller: _scrollController,
                    padding: const EdgeInsets.all(8),
                    itemCount: logs.length,
                    itemBuilder: (context, index) {
                      final log = logs[index];
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 1),
                        child: RichText(
                          text: TextSpan(
                            style: const TextStyle(
                              fontFamily: 'monospace',
                              fontSize: 11,
                            ),
                            children: [
                              TextSpan(
                                text: '${log.formattedTime} ',
                                style: const TextStyle(color: Colors.grey),
                              ),
                              TextSpan(
                                text: '${_getLevelIcon(log.level)} ',
                              ),
                              TextSpan(
                                text: '${log.tag}: ',
                                style: TextStyle(
                                  color: _getLogColor(log.level),
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                              TextSpan(
                                text: log.message,
                                style: const TextStyle(color: Colors.white),
                              ),
                            ],
                          ),
                        ),
                      );
                    },
                  ),
          ),
        ],
      ),
    );
  }
}
