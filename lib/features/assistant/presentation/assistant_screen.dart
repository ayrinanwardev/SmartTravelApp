import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/network/api_client.dart';
import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../data/models/trip.dart';
import '../../trips/providers/trips_provider.dart';
import '../providers/ai_travel_service.dart';

class _Msg {
  const _Msg.user(this.text) : fromUser = true, plan = null;
  const _Msg.ai(this.text, [this.plan]) : fromUser = false;
  final String text;
  final bool fromUser;
  final AiTripPlan? plan;
}

class AssistantScreen extends ConsumerStatefulWidget {
  const AssistantScreen({super.key});

  @override
  ConsumerState<AssistantScreen> createState() => _AssistantScreenState();
}

class _AssistantScreenState extends ConsumerState<AssistantScreen> {
  final _input = TextEditingController();
  final _scroll = ScrollController();
  final List<_Msg> _messages = [
    const _Msg.ai("Hi! I'm your Wanderly travel assistant ✨\nTell me where, how long and your budget, and I'll draft an itinerary with cost estimates."),
  ];
  bool _thinking = false;

  static const _suggestions = [
    "Plan a 4-day trip to Cox's Bazar under ৳20,000",
    'Weekend in Sajek for 4 friends',
    '5 days in Bali for a couple, budget 2 lakh',
    'Suggest a mountain getaway',
  ];

  @override
  void dispose() {
    _input.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _send([String? preset]) async {
    final text = (preset ?? _input.text).trim();
    if (text.isEmpty || _thinking) return;
    _input.clear();
    setState(() {
      _messages.add(_Msg.user(text));
      _thinking = true;
    });
    _scrollToEnd();
    AiReply reply;
    try {
      await ref.read(destinationsProvider.future);
      reply = await ref.read(aiTravelServiceProvider).ask(text);
    } catch (e) {
      reply = AiReply(text: '⚠️ ${ApiException.from(e).message}');
    }
    if (!mounted) return;
    setState(() {
      _messages.add(_Msg.ai(reply.text, reply.plan));
      _thinking = false;
    });
    _scrollToEnd();
  }

  void _scrollToEnd() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent + 400, duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
      }
    });
  }

  Future<void> _saveAsTrip(AiTripPlan plan) async {
    final start = DateUtils.dateOnly(DateTime.now()).add(const Duration(days: 14));
    final trip = Trip(
      id: uuid.v4(),
      destinationId: plan.destination.id,
      title: '${plan.destination.name} · AI plan',
      imageUrl: plan.destination.imageUrl,
      startDate: start,
      endDate: start.add(Duration(days: plan.days - 1)),
      travelers: plan.travelers,
      budget: plan.requestedBudget ?? plan.estimate,
      days: plan.itinerary,
    );
    final saved = await runApi(context, ref.read(tripsProvider.notifier).add(trip));
    if (saved != null && mounted) context.push('/trip/${saved.id}');
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            CircleAvatar(
              radius: 16,
              backgroundColor: AppColors.coral,
              child: Icon(Icons.auto_awesome_rounded, color: Colors.white, size: 18),
            ),
            SizedBox(width: 10),
            Text('AI Travel Assistant'),
          ],
        ),
      ),
      body: Column(
        children: [
          Expanded(
            child: ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
              itemCount: _messages.length + (_thinking ? 1 : 0) + (_messages.length == 1 ? 1 : 0),
              itemBuilder: (_, i) {
                if (i < _messages.length) {
                  final m = _messages[i];
                  return Column(
                    crossAxisAlignment: m.fromUser ? CrossAxisAlignment.end : CrossAxisAlignment.start,
                    children: [
                      _Bubble(m),
                      if (m.plan != null) _PlanCard(plan: m.plan!, onSave: () => _saveAsTrip(m.plan!)),
                    ],
                  );
                }
                if (_thinking) return const _Typing();
                return Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Wrap(
                    spacing: 8,
                    runSpacing: 8,
                    children: [
                      for (final s in _suggestions)
                        ActionChip(
                          avatar: const Icon(Icons.bolt_rounded, size: 16, color: AppColors.coral),
                          label: Text(s),
                          onPressed: () => _send(s),
                        ),
                    ],
                  ),
                );
              },
            ),
          ),
          SafeArea(
            top: false,
            child: Container(
              padding: const EdgeInsets.fromLTRB(16, 10, 10, 10),
              decoration: const BoxDecoration(
                color: AppColors.surface,
                border: Border(top: BorderSide(color: AppColors.border)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: TextField(
                      controller: _input,
                      minLines: 1,
                      maxLines: 4,
                      textInputAction: TextInputAction.send,
                      onSubmitted: (_) => _send(),
                      decoration: const InputDecoration(hintText: 'Ask me to plan a trip…'),
                    ),
                  ),
                  const SizedBox(width: 8),
                  IconButton.filled(
                    style: IconButton.styleFrom(backgroundColor: AppColors.coral, minimumSize: const Size(52, 52)),
                    onPressed: _thinking ? null : _send,
                    icon: const Icon(Icons.send_rounded),
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

class _Bubble extends StatelessWidget {
  const _Bubble(this.m);
  final _Msg m;

  @override
  Widget build(BuildContext context) {
    return Container(
      margin: const EdgeInsets.symmetric(vertical: 6),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      constraints: BoxConstraints(maxWidth: MediaQuery.sizeOf(context).width * 0.8),
      decoration: BoxDecoration(
        color: m.fromUser ? AppColors.navy : AppColors.surface,
        border: m.fromUser ? null : Border.all(color: AppColors.border),
        borderRadius: BorderRadius.only(
          topLeft: const Radius.circular(20),
          topRight: const Radius.circular(20),
          bottomLeft: Radius.circular(m.fromUser ? 20 : 4),
          bottomRight: Radius.circular(m.fromUser ? 4 : 20),
        ),
      ),
      child: Text(m.text, style: TextStyle(color: m.fromUser ? Colors.white : AppColors.textPrimary, height: 1.4)),
    );
  }
}

class _Typing extends StatelessWidget {
  const _Typing();

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: Alignment.centerLeft,
      child: Container(
        margin: const EdgeInsets.symmetric(vertical: 6),
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
        decoration: BoxDecoration(
          color: AppColors.surface,
          border: Border.all(color: AppColors.border),
          borderRadius: BorderRadius.circular(20),
        ),
        child: const Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.coral)),
            SizedBox(width: 10),
            Text('Planning your trip…'),
          ],
        ),
      ),
    );
  }
}

class _PlanCard extends StatelessWidget {
  const _PlanCard({required this.plan, required this.onSave});
  final AiTripPlan plan;
  final VoidCallback onSave;

  @override
  Widget build(BuildContext context) {
    final text = Theme.of(context).textTheme;
    return Card(
      margin: const EdgeInsets.only(top: 4, bottom: 12),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Stack(
            children: [
              NetImage(plan.destination.imageUrl, height: 120, width: double.infinity),
              const Positioned.fill(child: DecoratedBox(decoration: BoxDecoration(gradient: AppColors.heroGradient))),
              Positioned(
                left: 16,
                bottom: 12,
                right: 16,
                child: Row(
                  children: [
                    Expanded(
                      child: Text('${plan.days} days in ${plan.destination.name}',
                          style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w800)),
                    ),
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                      decoration: BoxDecoration(
                        color: plan.fitsBudget ? AppColors.success : AppColors.warning,
                        borderRadius: BorderRadius.circular(20),
                      ),
                      child: Text(plan.fitsBudget ? 'Within budget' : 'Over budget',
                          style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w700)),
                    ),
                  ],
                ),
              ),
            ],
          ),
          Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Estimated budget', style: text.titleSmall),
                const SizedBox(height: 8),
                for (final e in plan.breakdown.entries)
                  Padding(
                    padding: const EdgeInsets.symmetric(vertical: 2),
                    child: Row(
                      children: [
                        Expanded(child: Text(e.key)),
                        Text(Fmt.bdt(e.value), style: const TextStyle(fontWeight: FontWeight.w600)),
                      ],
                    ),
                  ),
                const Divider(height: 16),
                Row(
                  children: [
                    const Expanded(child: Text('Total', style: TextStyle(fontWeight: FontWeight.w800))),
                    Text(Fmt.bdt(plan.estimate), style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.coral)),
                  ],
                ),
                const SizedBox(height: 14),
                Text('Itinerary', style: text.titleSmall),
                for (final d in plan.itinerary)
                  Padding(
                    padding: const EdgeInsets.only(top: 10),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Day ${d.dayNumber}', style: const TextStyle(fontWeight: FontWeight.w800, color: AppColors.ocean)),
                        for (final a in d.activities)
                          Padding(
                            padding: const EdgeInsets.only(top: 4),
                            child: Row(
                              children: [
                                Icon(activityIcon(a.type), size: 16, color: activityColor(a.type)),
                                const SizedBox(width: 8),
                                Text(a.time, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w700)),
                                const SizedBox(width: 8),
                                Expanded(child: Text(a.title, style: const TextStyle(fontSize: 13))),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                if (plan.tips.isNotEmpty) ...[
                  const SizedBox(height: 14),
                  Text('Tips', style: text.titleSmall),
                  for (final t in plan.tips)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Row(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          const Text('•  '),
                          Expanded(child: Text(t, style: const TextStyle(fontSize: 13))),
                        ],
                      ),
                    ),
                ],
                const SizedBox(height: 16),
                FilledButton.icon(
                  onPressed: onSave,
                  icon: const Icon(Icons.bookmark_add_rounded),
                  label: const Text('Save as trip'),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
