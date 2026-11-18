/// Curated Unsplash photography. Every image widget falls back to a branded
/// gradient if a photo can't be loaded, so the app still looks right offline.
abstract final class Img {
  static String u(String id, {int w = 1000}) =>
      'https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&q=80';

  // Onboarding
  static final onboarding1 = u('photo-1488646953014-85cb44e25828');
  static final onboarding2 = u('photo-1469854523086-cc02fe5d8800');
  static final onboarding3 = u('photo-1501785888041-af3ef285b470');

  // Destinations
  static final beach = u('photo-1507525428034-b723cf961d3e');
  static final beach2 = u('photo-1506929562872-bb421503ef21');
  static final cloudsMountain = u('photo-1464822759023-fed622ff2c3b');
  static final greenHills = u('photo-1433086966358-54859d0ed716');
  static final desert = u('photo-1500530855697-b586d89ba3ee');
  static final forest = u('photo-1448375240586-882707db888b');
  static final bali = u('photo-1537996194471-e657df975ab4');
  static final maldives = u('photo-1514282401047-d79a71a590e8');
  static final kyoto = u('photo-1493976040374-85c8e12f0c0e');
  static final tokyo = u('photo-1540959733332-eab4deabeeaf');
  static final paris = u('photo-1502602898657-3e91760cbb34');
  static final alps = u('photo-1476514525535-07fb3b4ae5f1');
  static final dubai = u('photo-1512453979798-5ea266f8880c');
  static final santorini = u('photo-1570077188670-e3a8d69ac5ff');
  static final nepal = u('photo-1544735716-392fe2489ffa');
  static final venice = u('photo-1523906834658-6e24ef2386f9');
  static final lake = u('photo-1501785888041-af3ef285b470');
  static final roadTrip = u('photo-1469474968028-56623f02e42e');

  // Hotels
  static final hotelPool = u('photo-1566073771259-6a8506099945');
  static final hotelLobby = u('photo-1551882547-ff40c63fe5fa');
  static final resort = u('photo-1520250497591-112f2f40a3f4');
  static final hotelRoom = u('photo-1542314831-068cd1dbfeeb');
  static final hotelSuite = u('photo-1582719478250-c89cae4dc85b');
  static final hotelBed = u('photo-1571896349842-33c89424de2d');

  static final food = u('photo-1504674900247-0877df9cc836');
}
