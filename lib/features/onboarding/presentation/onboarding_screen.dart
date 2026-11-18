import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/widgets/common.dart';
import '../../../data/mock/images.dart';
import '../../auth/providers/auth_provider.dart';

class _Slide {
  const _Slide(this.image, this.eyebrow, this.title, this.body);
  final String image;
  final String eyebrow;
  final String title;
  final String body;
}

class OnboardingScreen extends ConsumerStatefulWidget {
  const OnboardingScreen({super.key});

  @override
  ConsumerState<OnboardingScreen> createState() => _OnboardingScreenState();
}

class _OnboardingScreenState extends ConsumerState<OnboardingScreen> {
  final _controller = PageController();
  int _page = 0;

  late final _slides = [
    _Slide(Img.onboarding1, 'DISCOVER', 'Find places that\nmove you', 'From the beaches of Cox\'s Bazar to the temples of Kyoto — curated destinations, real reviews and honest costs.'),
    _Slide(Img.onboarding2, 'PLAN', 'Plan every day,\nyour way', 'Build day-by-day itineraries, reorder activities, book stays and keep your budget on track.'),
    _Slide(Img.onboarding3, 'REMEMBER', 'Let AI do the\nheavy lifting', 'Ask our travel assistant for a 4-day plan under ৳20,000 and get an itinerary in seconds.'),
  ];

  Future<void> _finish() async {
    await ref.read(onboardingDoneProvider.notifier).complete();
    if (!mounted) return;
    context.go(ref.read(authProvider) == null ? '/login' : '/home');
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final last = _page == _slides.length - 1;
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          PageView.builder(
            controller: _controller,
            itemCount: _slides.length,
            onPageChanged: (i) => setState(() => _page = i),
            itemBuilder: (_, i) => Stack(
              fit: StackFit.expand,
              children: [
                NetImage(_slides[i].image),
                const DecoratedBox(
                  decoration: BoxDecoration(
                    gradient: LinearGradient(
                      begin: Alignment.topCenter,
                      end: Alignment.bottomCenter,
                      stops: [0, 0.4, 1],
                      colors: [Color(0x55000000), Color(0x00000000), Color(0xF00B2545)],
                    ),
                  ),
                ),
              ],
            ),
          ),
          SafeArea(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(24, 12, 24, 24),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Row(
                    children: [
                      const Text('Wanderly',
                          style: TextStyle(color: Colors.white, fontSize: 22, fontWeight: FontWeight.w800)),
                      const Spacer(),
                      if (!last)
                        TextButton(
                          onPressed: _finish,
                          child: const Text('Skip', style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600)),
                        ),
                    ],
                  ),
                  const Spacer(),
                  AnimatedSwitcher(
                    duration: const Duration(milliseconds: 300),
                    child: Column(
                      key: ValueKey(_page),
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(_slides[_page].eyebrow,
                            style: const TextStyle(color: AppColors.sunset, letterSpacing: 3, fontWeight: FontWeight.w700)),
                        const SizedBox(height: 10),
                        Text(_slides[_page].title,
                            style: const TextStyle(color: Colors.white, fontSize: 36, height: 1.1, fontWeight: FontWeight.w800, letterSpacing: -1)),
                        const SizedBox(height: 14),
                        Text(_slides[_page].body,
                            style: const TextStyle(color: Colors.white70, fontSize: 15, height: 1.5)),
                      ],
                    ),
                  ),
                  const SizedBox(height: 28),
                  Row(
                    children: [
                      for (var i = 0; i < _slides.length; i++)
                        AnimatedContainer(
                          duration: const Duration(milliseconds: 250),
                          margin: const EdgeInsets.only(right: 6),
                          height: 8,
                          width: i == _page ? 28 : 8,
                          decoration: BoxDecoration(
                            color: i == _page ? AppColors.coral : Colors.white38,
                            borderRadius: BorderRadius.circular(8),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 24),
                  FilledButton(
                    style: FilledButton.styleFrom(backgroundColor: AppColors.coral),
                    onPressed: last
                        ? _finish
                        : () => _controller.nextPage(duration: const Duration(milliseconds: 350), curve: Curves.easeOut),
                    child: Text(last ? 'Get Started' : 'Next'),
                  ),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}
