import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:webview_flutter/webview_flutter.dart';

import '../../../core/network/api_client.dart';
import '../../../core/network/realtime_client.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/hotel.dart';
import '../../notifications/providers/notifications_provider.dart';
import '../providers/bookings_provider.dart';

/// Pays a booking through SSLCommerz's hosted checkout (cards, bKash, Nagad, Rocket…).
///
/// The page opens in an in-app browser. We finish when either the gateway redirects to the
/// backend's `/payments/result` page, or the server pushes `booking.updated` over the
/// WebSocket — whichever comes first. The server, not this screen, decides "paid".
class PaymentScreen extends ConsumerStatefulWidget {
  const PaymentScreen({super.key, required this.bookingId});

  final String bookingId;

  @override
  ConsumerState<PaymentScreen> createState() => _PaymentScreenState();
}

class _PaymentScreenState extends ConsumerState<PaymentScreen> {
  WebViewController? _web;
  String? _error;
  int _progress = 0;
  bool _finishing = false;
  StreamSubscription<RealtimeEvent>? _live;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _start());
  }

  Future<void> _start() async {
    final api = ref.read(apiProvider);
    if (api == null) return; // offline demo mode renders a simulated checkout
    _live = ref.read(realtimeProvider)?.on('booking.updated').listen((e) {
      if (e.data['id'] == widget.bookingId && e.data['paymentStatus'] == 'paid') _finish('success');
    });
    try {
      final session = await api.startPayment(widget.bookingId);
      final controller = WebViewController()
        ..setJavaScriptMode(JavaScriptMode.unrestricted)
        ..setNavigationDelegate(NavigationDelegate(
          onProgress: (p) => mounted ? setState(() => _progress = p) : null,
          onNavigationRequest: (req) {
            final uri = Uri.tryParse(req.url);
            if (uri == null) return NavigationDecision.navigate;
            // Back on our server: read the outcome and close checkout.
            if (uri.path.endsWith('/payments/result')) {
              _finish(uri.queryParameters['status'] ?? 'failed');
              return NavigationDecision.prevent;
            }
            // Wallet apps (bKash/Nagad) and intent:// links open outside the web view.
            if (uri.scheme != 'http' && uri.scheme != 'https') {
              launchUrl(uri, mode: LaunchMode.externalApplication);
              return NavigationDecision.prevent;
            }
            return NavigationDecision.navigate;
          },
        ))
        ..loadRequest(Uri.parse(session.gatewayUrl));
      if (mounted) setState(() => _web = controller);
    } catch (e) {
      if (mounted) setState(() => _error = ApiException.from(e).message);
    }
  }

  /// Re-reads the booking from the server so the shown status is the validated one.
  Future<void> _finish(String status) async {
    if (_finishing || !mounted) return;
    _finishing = true;
    final api = ref.read(apiProvider);
    if (api != null) {
      try {
        ref.read(bookingsProvider.notifier).upsert(await api.booking(widget.bookingId));
      } catch (_) {}
    }
    if (!mounted) return;
    if (status == 'success') {
      context.pushReplacement('/booking-confirmed/${widget.bookingId}');
    } else {
      context.pop();
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(status == 'cancelled'
            ? 'Payment cancelled — your booking is saved, pay any time from Booking history.'
            : 'Payment failed — no money was taken. You can try again.'),
      ));
    }
  }

  @override
  void dispose() {
    _live?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final booking = ref.watch(bookingsProvider.select((list) => list.where((b) => b.id == widget.bookingId).firstOrNull));
    final offline = ref.watch(apiProvider) == null;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Secure payment'),
        leading: IconButton(icon: const Icon(Icons.close_rounded), onPressed: () => _finishing ? null : _finish('cancelled')),
        bottom: _web != null && _progress < 100
            ? PreferredSize(preferredSize: const Size.fromHeight(3), child: LinearProgressIndicator(value: _progress / 100))
            : null,
      ),
      body: offline
          ? _DemoCheckout(booking: booking, onPaid: () {
              ref.read(bookingsProvider.notifier).markPaidOffline(widget.bookingId);
              _finish('success');
            })
          : _error != null
              ? EmptyState(
                  icon: Icons.error_outline_rounded,
                  title: 'Couldn\'t start payment',
                  message: _error!,
                  action: 'Try again',
                  onAction: () {
                    setState(() => _error = null);
                    _start();
                  },
                )
              : _web == null
                  ? const Center(child: CircularProgressIndicator())
                  : WebViewWidget(controller: _web!),
    );
  }
}

class _DemoCheckout extends StatelessWidget {
  const _DemoCheckout({required this.booking, required this.onPaid});

  final Booking? booking;
  final VoidCallback onPaid;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const IconBubble(icon: Icons.payments_rounded, color: AppColors.success, size: 80),
          const SizedBox(height: 20),
          Text('Offline demo checkout', style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 8),
          const Text('Run with the backend to pay through the SSLCommerz sandbox (bKash, Nagad, cards).',
              textAlign: TextAlign.center),
          const SizedBox(height: 24),
          FilledButton(
            onPressed: booking == null ? null : onPaid,
            child: Text('Simulate payment · ${Fmt.bdt(booking?.total ?? 0)}'),
          ),
        ],
      ),
    );
  }
}
