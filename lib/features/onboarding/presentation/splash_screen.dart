import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/theme/app_colors.dart';
import '../../auth/providers/auth_provider.dart';

class WanderlyLogo extends StatelessWidget {
  const WanderlyLogo({super.key, this.size = 72, this.light = true});

  final double size;
  final bool light;

  @override
  Widget build(BuildContext context) {
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Container(
          width: size,
          height: size,
          decoration: BoxDecoration(
            gradient: AppColors.sunsetGradient,
            borderRadius: BorderRadius.circular(size * 0.3),
            boxShadow: [BoxShadow(color: AppColors.coral.withValues(alpha: 0.4), blurRadius: 24, offset: const Offset(0, 8))],
          ),
          child: Icon(Icons.travel_explore_rounded, color: Colors.white, size: size * 0.55),
        ),
      ],
    );
  }
}

class SplashScreen extends ConsumerStatefulWidget {
  const SplashScreen({super.key});

  @override
  ConsumerState<SplashScreen> createState() => _SplashScreenState();
}

class _SplashScreenState extends ConsumerState<SplashScreen> with SingleTickerProviderStateMixin {
  late final AnimationController _c = AnimationController(vsync: this, duration: const Duration(milliseconds: 1100))
    ..forward();

  @override
  void initState() {
    super.initState();
    Future.delayed(const Duration(milliseconds: 1800), () {
      if (!mounted) return;
      final onboarded = ref.read(onboardingDoneProvider);
      final user = ref.read(authProvider);
      context.go(!onboarded ? '/onboarding' : (user == null ? '/login' : '/home'));
    });
  }

  @override
  void dispose() {
    _c.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final fade = CurvedAnimation(parent: _c, curve: Curves.easeOut);
    return Scaffold(
      body: Container(
        decoration: const BoxDecoration(gradient: AppColors.brandGradient),
        child: Center(
          child: FadeTransition(
            opacity: fade,
            child: ScaleTransition(
              scale: Tween(begin: 0.85, end: 1.0).animate(fade),
              child: const Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  WanderlyLogo(size: 88),
                  SizedBox(height: 24),
                  Text('Wanderly',
                      style: TextStyle(color: Colors.white, fontSize: 40, fontWeight: FontWeight.w800, letterSpacing: -1)),
                  SizedBox(height: 6),
                  Text('EXPLORE BEYOND',
                      style: TextStyle(color: Colors.white70, fontSize: 13, letterSpacing: 4, fontWeight: FontWeight.w600)),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
