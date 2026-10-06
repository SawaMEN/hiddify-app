// ignore_for_file: avoid_print

import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:loggy/loggy.dart';

class ConsolePrinter extends LoggyPrinter {
  const ConsolePrinter({this.showColors = false});

  final bool showColors;

  static final _levelColors = {
    LogLevel.debug: AnsiColor(foregroundColor: AnsiColor.grey(0.5), italic: true),
    LogLevel.info: AnsiColor(foregroundColor: 35),
    LogLevel.warning: AnsiColor(foregroundColor: 214),
    LogLevel.error: AnsiColor(foregroundColor: 196),
  };

  @override
  void onLog(LogRecord record) {
    final colorize = showColors && stdout.supportsAnsiEscapes;
    final time = record.time.toIso8601String().split('T')[1];
    final callerFrame = record.callerFrame == null ? ' ' : ' (${record.callerFrame?.location}) ';

    final String logLevel;
    if (colorize) {
      logLevel = record.level.name.toUpperCase().padRight(8);
    } else {
      logLevel = "[${record.level.name.toUpperCase()}]".padRight(10);
    }

    final color = showColors ? levelColor(record.level) ?? AnsiColor() : AnsiColor();

    print(color('$time $logLevel [${record.loggerName}]$callerFrame${record.message}'));

    if (record.stackTrace != null) {
      print(record.stackTrace);
    }
  }

  AnsiColor? levelColor(LogLevel level) {
    return _levelColors[level];
  }
}

class FileLogPrinter extends LoggyPrinter {
  FileLogPrinter(String filePath, {this.minLevel = LogLevel.debug}) : _logFile = File(filePath) {
    try {
      if (_logFile.existsSync()) _bytes = _logFile.lengthSync();
      _open();
    } catch (_) {
      _closed = true;
    }
  }

  final File _logFile;
  final LogLevel minLevel;

  static const maxBytes = 2 * 1024 * 1024;
  int _bytes = 0;
  int _pendingWrites = 0;
  bool _closed = false;
  IOSink? _sink;
  Timer? _flushTimer;
  Future<void> _writes = Future<void>.value();

  void _open() {
    _sink = _logFile.openWrite(mode: FileMode.append);
    unawaited(
      _sink!.done.catchError((Object error, StackTrace stack) {
        _closed = true;
      }),
    );
  }

  @override
  void onLog(LogRecord record) {
    if (_closed || _pendingWrites >= 100 || record.level.priority < minLevel.priority) return;
    final message =
        '${record.time.toIso8601String()} - $record\n'
        '${record.error == null ? '' : '${record.error}\n'}'
        '${record.stackTrace == null ? '' : '${record.stackTrace}\n'}';
    final encoded = utf8.encode(message);
    final text = encoded.length > 65536
        ? '${utf8.decode(encoded.sublist(0, 65536), allowMalformed: true)}\n[log record truncated]\n'
        : message;
    final bytes = utf8.encode(text).length;
    _pendingWrites++;
    _writes = _writes
        .then((_) async {
          if (_closed) return;
          if (_bytes + bytes > maxBytes) {
            await _sink?.close();
            final previous = File('${_logFile.path}.previous');
            if (await previous.exists()) await previous.delete();
            if (await _logFile.exists()) await _logFile.rename(previous.path);
            _bytes = 0;
            _open();
          }
          _sink?.write(text);
          _bytes += bytes;
          if (record.level.priority >= LogLevel.error.priority) await _sink?.flush();
          _flushTimer ??= Timer(const Duration(milliseconds: 250), () {
            _flushTimer = null;
            _writes = _writes
                .then((_) async {
                  if (!_closed) await _sink?.flush();
                })
                .catchError((Object error, StackTrace stack) {
                  _closed = true;
                });
          });
        })
        .catchError((Object error, StackTrace stack) {
          _closed = true;
        })
        .whenComplete(() {
          _pendingWrites--;
        });
  }

  void dispose() {
    _flushTimer?.cancel();
    unawaited(
      _writes
          .then((_) async {
            _closed = true;
            await _sink?.close();
          })
          .catchError((Object error, StackTrace stack) {}),
    );
  }
}
