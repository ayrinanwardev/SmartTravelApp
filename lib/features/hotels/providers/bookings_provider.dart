import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/remote_sync.dart';
import '../../../core/providers.dart';
import '../../../data/local/local_store.dart';
import '../../../data/models/hotel.dart';

class BookingsNotifier extends Notifier<List<Booking>> with RemoteSync<List<Booking>> {
  static const taxRate = 0.10;

  @override
  List<Booking> build() {
    if (watchApi() != null) return loadRemote(const []);
    return ref.watch(localStoreProvider).readList(LocalStore.bookingsKey, Booking.fromJson) ?? const [];
  }

  @override
  Future<void> refresh() => safeRefresh((a) => a.bookings());

  /// Inserts or replaces a booking (e.g. live payment status from the WebSocket).
  void upsert(Booking b) {
    final exists = state.any((x) => x.id == b.id);
    final next = exists ? [for (final x in state) x.id == b.id ? b : x] : [b, ...state];
    state = next;
    if (api == null) ref.read(localStoreProvider).writeList(LocalStore.bookingsKey, next, (x) => x.toJson());
  }

  Booking? byId(String id) => state.where((b) => b.id == id).firstOrNull;

  /// Offline demo mode only: simulates a successful gateway payment.
  void markPaidOffline(String id) {
    final b = byId(id);
    if (b != null) upsert(b.copyWith(paymentStatus: PaymentStatus.paid, paymentMethod: 'DEMO-Offline'));
  }

  /// Books a stay. The server prices it and issues the confirmation code in API mode;
  /// offline mode applies the same pricing rule (nightly rate × nights × rooms + 10% tax).
  Future<Booking> book({
    required Hotel hotel,
    required DateTime checkIn,
    required DateTime checkOut,
    required int guests,
    required int rooms,
  }) async {
    final client = api;
    final Booking booking;
    if (client != null) {
      booking = await client.book(hotelId: hotel.id, checkIn: checkIn, checkOut: checkOut, guests: guests, rooms: rooms);
    } else {
      final subtotal = hotel.pricePerNight * checkOut.difference(checkIn).inDays * rooms;
      booking = Booking(
        id: 'WND-${DateTime.now().millisecondsSinceEpoch.toString().substring(5)}',
        hotelId: hotel.id,
        hotelName: hotel.name,
        imageUrl: hotel.imageUrls.first,
        checkIn: checkIn,
        checkOut: checkOut,
        guests: guests,
        rooms: rooms,
        total: subtotal + (subtotal * taxRate).round(),
        createdAt: DateTime.now(),
      );
    }
    final next = [booking, ...state];
    state = next;
    if (client == null) {
      await ref.read(localStoreProvider).writeList(LocalStore.bookingsKey, next, (x) => x.toJson());
    }
    return booking;
  }
}

final bookingsProvider = NotifierProvider<BookingsNotifier, List<Booking>>(BookingsNotifier.new);
