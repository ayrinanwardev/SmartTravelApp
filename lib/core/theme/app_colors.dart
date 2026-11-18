import 'package:material_ui/material_ui.dart';

/// Wanderly brand palette — warm off-white canvas, deep ocean navy, sunset coral.
abstract final class AppColors {
  static const background = Color(0xFFFAF7F2);
  static const surface = Color(0xFFFFFFFF);
  static const surfaceMuted = Color(0xFFF2EEE7);

  static const navy = Color(0xFF0B2545);
  static const ocean = Color(0xFF13497A);
  static const oceanLight = Color(0xFF3D7CB8);

  static const coral = Color(0xFFFF6B4A);
  static const sunset = Color(0xFFFF9F43);

  static const textPrimary = Color(0xFF14213D);
  static const textSecondary = Color(0xFF6B7280);
  static const border = Color(0xFFE7E1D8);

  static const success = Color(0xFF2E9E6A);
  static const warning = Color(0xFFF2A93B);
  static const danger = Color(0xFFE5484D);
  static const star = Color(0xFFFFB800);

  static const heroGradient = LinearGradient(
    begin: Alignment.topCenter,
    end: Alignment.bottomCenter,
    colors: [Color(0x00000000), Color(0xCC0B2545)],
  );

  static const brandGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [navy, ocean],
  );

  static const sunsetGradient = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [coral, sunset],
  );

  /// Chart / category palette.
  static const chart = [
    Color(0xFF13497A),
    Color(0xFFFF6B4A),
    Color(0xFF2E9E6A),
    Color(0xFFF2A93B),
    Color(0xFF8B5CF6),
    Color(0xFF64748B),
  ];
}
