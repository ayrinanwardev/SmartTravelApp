import 'dart:async';
import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

/// One server event: `{"type": "notification.created", "data": {...}}`.
class RealtimeEvent {
  const RealtimeEvent(this.type, this.data);

  final String type;
  final Map<String, dynamic> data;

  @override
  String toString() => 'RealtimeEvent($type)';
}

/// WebSocket connection to the backend (`/ws?token=<JWT>`).
///
/// - Reconnects automatically with exponential backoff (1 s → 30 s) — e.g. when
///   Render's free instance wakes up or the phone switches networks.
/// - Sends a ping every 25 s so proxies don't drop an idle connection.
/// - Exposes a broadcast [events] stream and a [connected] flag for the UI.
class RealtimeClient {
  RealtimeClient({required this.baseUrl, required this.token});

  final String baseUrl;

  /// Read on every (re)connect so a refreshed JWT is used.
  final String Function() token;

  final _events = StreamController<RealtimeEvent>.broadcast();
  final connected = ValueNotifier<bool>(false);

  WebSocketChannel? _channel;
  StreamSubscription<dynamic>? _sub;
  Timer? _ping;
  Timer? _retry;
  Duration _backoff = const Duration(seconds: 1);
  bool _stopped = true;

  Stream<RealtimeEvent> get events => _events.stream;

  Stream<RealtimeEvent> on(String type) => events.where((e) => e.type == type);

  Uri get _uri {
    final base = Uri.parse(baseUrl);
    return base.replace(
      scheme: base.scheme == 'https' ? 'wss' : 'ws',
      path: '/ws',
      queryParameters: {'token': token()},
    );
  }

  void start() {
    if (!_stopped) return;
    _stopped = false;
    _connect();
  }

  Future<void> _connect() async {
    if (_stopped) return;
    try {
      final channel = WebSocketChannel.connect(_uri);
      await channel.ready;
      if (_stopped) {
        await channel.sink.close();
        return;
      }
      _channel = channel;
      _backoff = const Duration(seconds: 1);
      connected.value = true;
      _sub = channel.stream.listen(_onFrame, onDone: _onClosed, onError: (_) => _onClosed(), cancelOnError: true);
      _ping = Timer.periodic(const Duration(seconds: 25), (_) => send({'type': 'ping'}));
      debugPrint('[realtime] connected');
    } catch (e) {
      debugPrint('[realtime] connect failed: $e');
      _scheduleRetry();
    }
  }

  void _onFrame(dynamic frame) {
    try {
      final json = jsonDecode(frame as String) as Map<String, dynamic>;
      final data = json['data'];
      _events.add(RealtimeEvent(json['type'] as String, data is Map<String, dynamic> ? data : const {}));
    } catch (e) {
      debugPrint('[realtime] bad frame: $e');
    }
  }

  void _onClosed() {
    _cleanup();
    if (!_stopped) _scheduleRetry();
  }

  void _scheduleRetry() {
    _retry?.cancel();
    _retry = Timer(_backoff, _connect);
    final next = _backoff * 2;
    _backoff = next > const Duration(seconds: 30) ? const Duration(seconds: 30) : next;
  }

  void _cleanup() {
    _ping?.cancel();
    _sub?.cancel();
    _channel = null;
    connected.value = false;
  }

  /// Sends a JSON message if connected (silently dropped otherwise — callers re-send state).
  void send(Map<String, dynamic> message) {
    final c = _channel;
    if (c == null) return;
    try {
      c.sink.add(jsonEncode(message));
    } catch (_) {}
  }

  Future<void> stop() async {
    _stopped = true;
    _retry?.cancel();
    final c = _channel;
    _cleanup();
    await c?.sink.close();
  }

  Future<void> dispose() async {
    await stop();
    await _events.close();
    connected.dispose();
  }
}
