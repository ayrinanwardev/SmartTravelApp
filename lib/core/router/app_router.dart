import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../data/models/destination.dart';
import '../../features/assistant/presentation/assistant_screen.dart';
import '../../features/auth/providers/auth_provider.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/budget/presentation/budget_screen.dart';
import '../../features/destination/presentation/destination_details_screen.dart';
import '../../features/explore/presentation/explore_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import '../../features/hotels/presentation/hotels_screens.dart';
import '../../features/journal/presentation/journal_screen.dart';
import '../../features/onboarding/presentation/onboarding_screen.dart';
import '../../features/onboarding/presentation/splash_screen.dart';
import '../../features/profile/presentation/profile_screen.dart';
import '../../features/saved/presentation/saved_screen.dart';
import '../../features/shell/presentation/app_shell.dart';
import '../../features/tools/presentation/currency_screen.dart';
import '../../features/tools/presentation/flights_screen.dart';
import '../../features/tools/presentation/packing_screen.dart';
import '../../features/trips/presentation/my_trips_screen.dart';
import '../../features/trips/presentation/trip_detail_screen.dart';
import '../../features/trips/presentation/trip_planner_screen.dart';

final _rootKey = GlobalKey<NavigatorState>();

final routerProvider = Provider<GoRouter>((ref) {
  // Re-evaluates the redirect whenever the user signs in or out (incl. an expired JWT).
  final signedIn = ValueNotifier<bool>(ref.read(authProvider) != null);
  ref.listen(authProvider, (_, user) => signedIn.value = user != null);
  ref.onDispose(signedIn.dispose);
  const publicRoutes = {'/splash', '/onboarding', '/login'};

  return GoRouter(
    navigatorKey: _rootKey,
    initialLocation: '/splash',
    refreshListenable: signedIn,
    redirect: (_, state) {
      final location = state.matchedLocation;
      if (!signedIn.value && !publicRoutes.contains(location)) return '/login';
      if (signedIn.value && location == '/login') return '/home';
      return null;
    },
    routes: [
      GoRoute(path: '/splash', builder: (_, _) => const SplashScreen()),
      GoRoute(path: '/onboarding', builder: (_, _) => const OnboardingScreen()),
      GoRoute(path: '/login', builder: (_, _) => const LoginScreen()),
      StatefulShellRoute.indexedStack(
        builder: (_, _, shell) => AppShell(navigationShell: shell),
        branches: [
          StatefulShellBranch(routes: [GoRoute(path: '/home', builder: (_, _) => const HomeScreen())]),
          StatefulShellBranch(routes: [
            GoRoute(
              path: '/explore',
              builder: (_, s) {
                final cat = s.uri.queryParameters['category'];
                return ExploreScreen(
                  initialCategory: DestinationCategory.values.where((c) => c.name == cat).firstOrNull,
                  autofocus: s.uri.queryParameters['focus'] == '1',
                );
              },
            ),
          ]),
          StatefulShellBranch(routes: [GoRoute(path: '/trips', builder: (_, _) => const MyTripsScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/saved', builder: (_, _) => const SavedScreen())]),
          StatefulShellBranch(routes: [GoRoute(path: '/profile', builder: (_, _) => const ProfileScreen())]),
        ],
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/destination/:id',
        builder: (_, s) => DestinationDetailsScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/plan',
        builder: (_, s) => TripPlannerScreen(destinationId: s.uri.queryParameters['dest']),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/trip/:id',
        builder: (_, s) => TripDetailScreen(tripId: s.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/hotels',
        builder: (_, s) => HotelsScreen(destinationId: s.uri.queryParameters['dest']),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/hotel/:id',
        builder: (_, s) => HotelDetailScreen(id: s.pathParameters['id']!),
      ),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/booking-confirmed/:id',
        builder: (_, s) => BookingConfirmedScreen(bookingId: s.pathParameters['id']!),
      ),
      GoRoute(parentNavigatorKey: _rootKey, path: '/bookings', builder: (_, _) => const BookingHistoryScreen()),
      GoRoute(parentNavigatorKey: _rootKey, path: '/budget', builder: (_, _) => const BudgetScreen()),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/journal',
        builder: (_, _) => const JournalScreen(),
        routes: [
          GoRoute(parentNavigatorKey: _rootKey, path: 'new', builder: (_, _) => const NewJournalEntryScreen()),
          GoRoute(
            parentNavigatorKey: _rootKey,
            path: ':id',
            builder: (_, s) => JournalEntryScreen(id: s.pathParameters['id']!),
          ),
        ],
      ),
      GoRoute(parentNavigatorKey: _rootKey, path: '/assistant', builder: (_, _) => const AssistantScreen()),
      GoRoute(parentNavigatorKey: _rootKey, path: '/flights', builder: (_, _) => const FlightsScreen()),
      GoRoute(parentNavigatorKey: _rootKey, path: '/currency', builder: (_, _) => const CurrencyScreen()),
      GoRoute(
        parentNavigatorKey: _rootKey,
        path: '/packing',
        builder: (_, s) => PackingScreen(
          destinationId: s.uri.queryParameters['dest'],
          days: int.tryParse(s.uri.queryParameters['days'] ?? ''),
        ),
      ),
    ],
  );
});
