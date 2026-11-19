import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:material_ui/material_ui.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../../core/theme/app_colors.dart';

/// OpenStreetMap tiles — free, no API key. Their usage policy requires a real user agent
/// and visible attribution (see [MapAttribution]).
TileLayer osmTiles() => TileLayer(
      urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
      userAgentPackageName: 'com.wanderly.app',
      maxNativeZoom: 19,
    );

class MapAttribution extends StatelessWidget {
  const MapAttribution({super.key});

  @override
  Widget build(BuildContext context) => RichAttributionWidget(
        attributions: [
          TextSourceAttribution(
            'OpenStreetMap contributors',
            onTap: () => launchUrl(Uri.parse('https://openstreetmap.org/copyright')),
          ),
        ],
      );
}

/// Round map pin with an icon; [selected] makes it larger and outlined.
class MapPin extends StatelessWidget {
  const MapPin({super.key, required this.icon, required this.color, this.selected = false, this.label});

  final IconData icon;
  final Color color;
  final bool selected;
  final String? label;

  @override
  Widget build(BuildContext context) {
    final size = selected ? 46.0 : 36.0;
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        AnimatedContainer(
          duration: const Duration(milliseconds: 180),
          width: size,
          height: size,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: selected ? 4 : 3),
            boxShadow: const [BoxShadow(color: Color(0x40000000), blurRadius: 6, offset: Offset(0, 2))],
          ),
          child: Icon(icon, color: Colors.white, size: size * 0.5),
        ),
        if (label != null)
          Container(
            margin: const EdgeInsets.only(top: 2),
            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
            decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(8)),
            child: Text(label!, style: const TextStyle(fontSize: 10, fontWeight: FontWeight.w700), maxLines: 1),
          ),
      ],
    );
  }
}

/// Pulsing-style blue dot for "you are here".
class UserDot extends StatelessWidget {
  const UserDot({super.key, this.color = AppColors.oceanLight});

  final Color color;

  @override
  Widget build(BuildContext context) => Container(
        decoration: BoxDecoration(color: color.withValues(alpha: 0.25), shape: BoxShape.circle),
        alignment: Alignment.center,
        child: Container(
          width: 18,
          height: 18,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 3),
          ),
        ),
      );
}

/// Opens turn-by-turn directions in Google Maps (app if installed, otherwise the browser).
Future<void> openDirections(LatLng to, {LatLng? from}) async {
  final params = {
    'api': '1',
    'destination': '${to.latitude},${to.longitude}',
    if (from != null) 'origin': '${from.latitude},${from.longitude}',
  };
  await launchUrl(Uri.https('www.google.com', '/maps/dir/', params), mode: LaunchMode.externalApplication);
}
