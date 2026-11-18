import '../../core/network/wanderly_api.dart';
import '../mock/mock_data.dart';
import '../models/destination.dart';
import '../models/hotel.dart';

/// Contract the UI depends on. [ApiTravelRepository] talks to the Spring Boot
/// backend; [MockTravelRepository] serves bundled data for offline demo mode.
abstract interface class TravelRepository {
  Future<List<Destination>> getDestinations();
  Future<List<Hotel>> getHotels({String? destinationId});
}

class ApiTravelRepository implements TravelRepository {
  const ApiTravelRepository(this._api);

  final WanderlyApi _api;

  @override
  Future<List<Destination>> getDestinations() => _api.destinations();

  @override
  Future<List<Hotel>> getHotels({String? destinationId}) => _api.hotels(destinationId: destinationId);
}

class MockTravelRepository implements TravelRepository {
  const MockTravelRepository({this.latency = const Duration(milliseconds: 350)});

  final Duration latency;

  @override
  Future<List<Destination>> getDestinations() async {
    await Future<void>.delayed(latency);
    return MockData.destinations;
  }

  @override
  Future<List<Hotel>> getHotels({String? destinationId}) async {
    await Future<void>.delayed(latency);
    if (destinationId == null) return MockData.hotels;
    return MockData.hotels.where((h) => h.destinationId == destinationId).toList();
  }
}
