import 'package:flutter_test/flutter_test.dart';

import 'package:smart_travel_app/data/models/destination.dart';
import 'package:smart_travel_app/data/models/expense.dart';
import 'package:smart_travel_app/data/models/hotel.dart';
import 'package:smart_travel_app/data/models/journal_entry.dart';
import 'package:smart_travel_app/data/models/trip.dart';

/// JSON samples copied from real responses of the Spring Boot API.
void main() {
  test('Destination.fromJson parses GET /api/destinations?full=true items', () {
    final d = Destination.fromJson({
      'id': 'sajek',
      'name': 'Sajek Valley',
      'country': 'Bangladesh',
      'tagline': 'Wake up above a sea of clouds',
      'description': 'Perched on the hills…',
      'imageUrl': 'https://img/1',
      'gallery': ['https://img/1', 'https://img/2'],
      'category': 'mountain',
      'bestSeasons': ['monsoon', 'autumn'],
      'bestTime': 'June – November',
      'rating': 4.8,
      'reviewCount': 1920,
      'avgTempC': 22,
      'cost': {'transport': 2800, 'hotelPerNight': 3500, 'foodPerDay': 900, 'activitiesPerDay': 600},
      'typicalTripCost': 14300,
      'highlights': ['Sunrise above clouds'],
      'attractions': [
        {'id': 1, 'name': 'Konglak Para', 'type': 'Viewpoint', 'imageUrl': 'https://img/3', 'rating': 4.9, 'distanceKm': 2.0, 'latitude': null, 'longitude': null},
      ],
      'reviews': [
        {'id': 5, 'author': 'Farhan R.', 'rating': 5.0, 'comment': 'Worth it', 'date': '2025-09-18'},
      ],
      'isTrending': true,
      'isPopular': false,
    });
    expect(d.category, DestinationCategory.mountain);
    expect(d.bestSeasons, [Season.monsoon, Season.autumn]);
    expect(d.cost.hotelPerNight, 3500);
    expect(d.attractions.single.name, 'Konglak Para');
    expect(d.reviews.single.date, DateTime(2025, 9, 18));
    expect(d.isTrending, isTrue);
  });

  test('Trip.fromJson parses TripResponse with LocalDate strings', () {
    final t = Trip.fromJson({
      'id': 'b7c1',
      'destinationId': 'bali',
      'destinationName': 'Bali',
      'title': 'Bali Trip',
      'imageUrl': 'https://img/bali',
      'startDate': '2026-12-01',
      'endDate': '2026-12-03',
      'dayCount': 3,
      'travelers': 2,
      'budget': 90000,
      'plannedCost': 1500,
      'activityCount': 1,
      'upcoming': true,
      'days': [
        {
          'dayNumber': 1,
          'date': '2026-12-01',
          'activities': [
            {'id': 'a1', 'title': 'Rice terraces', 'time': '09:00', 'type': 'sightseeing', 'note': '', 'cost': 1500},
          ],
        },
        {'dayNumber': 2, 'date': '2026-12-02', 'activities': []},
        {'dayNumber': 3, 'date': '2026-12-03', 'activities': []},
      ],
    });
    expect(t.dayCount, 3);
    expect(t.days.first.activities.single.type, ActivityType.sightseeing);
  });

  test('Hotel, Booking, Expense and JournalEntry parse API payloads', () {
    final h = Hotel.fromJson({
      'id': 'h-ocean', 'destinationId': 'coxs-bazar', 'name': 'Ocean Breeze Inn', 'address': 'Kolatoli Road',
      'imageUrls': ['https://img/h'], 'pricePerNight': 3800, 'rating': 4.3, 'reviewCount': 712,
      'amenities': ['Free Wi-Fi'], 'description': 'Friendly', 'stars': 3,
    });
    expect(h.pricePerNight, 3800);

    final b = Booking.fromJson({
      'id': 'WND-12345678', 'hotelId': 'h-ocean', 'hotelName': 'Ocean Breeze Inn', 'imageUrl': 'https://img/h',
      'checkIn': '2026-11-21', 'checkOut': '2026-11-23', 'nights': 2, 'guests': 2, 'rooms': 1,
      'subtotal': 7600, 'taxes': 760, 'total': 8360, 'status': 'confirmed', 'createdAt': '2026-11-16T10:15:30.123Z',
    });
    expect(b.nights, 2);
    expect(b.total, 8360);

    final e = Expense.fromJson(
        {'id': 'x', 'title': 'Hotel', 'amount': 8500, 'category': 'hotel', 'date': '2026-11-16', 'tripId': null});
    expect(e.category, ExpenseCategory.hotel);

    final j = JournalEntry.fromApi({
      'id': 'j1', 'title': 'Clouds', 'body': 'Wow', 'location': 'Sajek', 'date': '2025-10-14', 'album': 'Hills',
      'photoUrls': ['/uploads/a.jpg', 'https://cdn/b.jpg'],
    }, (u) => u.startsWith('/') ? 'http://10.0.2.2:8080$u' : u);
    expect(j.photoPaths, ['http://10.0.2.2:8080/uploads/a.jpg', 'https://cdn/b.jpg']);
  });
}
