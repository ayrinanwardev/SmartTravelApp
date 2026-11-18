import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/providers.dart';
import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../../../core/widgets/destination_cards.dart';
import '../../../data/models/destination.dart';

enum SortBy { rating, priceLow, priceHigh }

class ExploreScreen extends ConsumerStatefulWidget {
  const ExploreScreen({super.key, this.initialCategory, this.autofocus = false});

  final DestinationCategory? initialCategory;
  final bool autofocus;

  @override
  ConsumerState<ExploreScreen> createState() => _ExploreScreenState();
}

class _ExploreScreenState extends ConsumerState<ExploreScreen> {
  static const _maxBudget = 250000.0;

  final _search = TextEditingController();
  final _focus = FocusNode();
  DestinationCategory? _category;
  double _budget = _maxBudget;
  final Set<Season> _seasons = {};
  SortBy _sort = SortBy.rating;

  @override
  void initState() {
    super.initState();
    _category = widget.initialCategory;
    if (widget.autofocus) WidgetsBinding.instance.addPostFrameCallback((_) => _focus.requestFocus());
  }

  @override
  void didUpdateWidget(covariant ExploreScreen old) {
    super.didUpdateWidget(old);
    if (widget.initialCategory != old.initialCategory && widget.initialCategory != null) {
      setState(() => _category = widget.initialCategory);
    }
    if (widget.autofocus && !old.autofocus) _focus.requestFocus();
  }

  @override
  void dispose() {
    _search.dispose();
    _focus.dispose();
    super.dispose();
  }

  int get _activeFilterCount => (_budget < _maxBudget ? 1 : 0) + _seasons.length + (_sort != SortBy.rating ? 1 : 0);

  List<Destination> _apply(List<Destination> all) {
    final q = _search.text.trim().toLowerCase();
    final list = all.where((d) {
      if (_category != null && d.category != _category) return false;
      if (q.isNotEmpty &&
          !d.name.toLowerCase().contains(q) &&
          !d.country.toLowerCase().contains(q) &&
          !d.tagline.toLowerCase().contains(q)) {
        return false;
      }
      if (d.typicalTripCost > _budget) return false;
      if (_seasons.isNotEmpty && !d.bestSeasons.any(_seasons.contains)) return false;
      return true;
    }).toList();
    switch (_sort) {
      case SortBy.rating:
        list.sort((a, b) => b.rating.compareTo(a.rating));
      case SortBy.priceLow:
        list.sort((a, b) => a.typicalTripCost.compareTo(b.typicalTripCost));
      case SortBy.priceHigh:
        list.sort((a, b) => b.typicalTripCost.compareTo(a.typicalTripCost));
    }
    return list;
  }

  Future<void> _openFilters() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setSheet) {
          void update(VoidCallback fn) {
            setSheet(fn);
            setState(() {});
          }

          return Padding(
            padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Text('Filters', style: Theme.of(ctx).textTheme.titleLarge),
                    const Spacer(),
                    TextButton(
                      onPressed: () => update(() {
                        _budget = _maxBudget;
                        _seasons.clear();
                        _sort = SortBy.rating;
                      }),
                      child: const Text('Reset'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text('Max budget (3-day trip, per person)', style: Theme.of(ctx).textTheme.titleSmall),
                Slider(
                  value: _budget,
                  min: 10000,
                  max: _maxBudget,
                  divisions: 24,
                  activeColor: AppColors.coral,
                  label: _budget >= _maxBudget ? 'Any' : Fmt.bdt(_budget),
                  onChanged: (v) => update(() => _budget = v),
                ),
                Text(_budget >= _maxBudget ? 'Any budget' : 'Up to ${Fmt.bdt(_budget)}',
                    style: const TextStyle(fontWeight: FontWeight.w700)),
                const SizedBox(height: 20),
                Text('Best season', style: Theme.of(ctx).textTheme.titleSmall),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final s in Season.values)
                      FilterChip(
                        label: Text(s.label),
                        selected: _seasons.contains(s),
                        onSelected: (v) => update(() => v ? _seasons.add(s) : _seasons.remove(s)),
                      ),
                  ],
                ),
                const SizedBox(height: 20),
                Text('Sort by', style: Theme.of(ctx).textTheme.titleSmall),
                const SizedBox(height: 8),
                SegmentedButton<SortBy>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: SortBy.rating, label: Text('Top rated')),
                    ButtonSegment(value: SortBy.priceLow, label: Text('Price ↑')),
                    ButtonSegment(value: SortBy.priceHigh, label: Text('Price ↓')),
                  ],
                  selected: {_sort},
                  onSelectionChanged: (s) => update(() => _sort = s.first),
                ),
                const SizedBox(height: 24),
                FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('Show results')),
              ],
            ),
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(destinationsProvider);
    return Scaffold(
      appBar: AppBar(title: const Text('Explore'), toolbarHeight: 64),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 12),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _search,
                    focusNode: _focus,
                    onChanged: (_) => setState(() {}),
                    textInputAction: TextInputAction.search,
                    decoration: InputDecoration(
                      hintText: 'Search by country or city',
                      prefixIcon: const Icon(Icons.search_rounded),
                      suffixIcon: _search.text.isEmpty
                          ? null
                          : IconButton(
                              icon: const Icon(Icons.close_rounded),
                              onPressed: () => setState(_search.clear),
                            ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                Badge(
                  isLabelVisible: _activeFilterCount > 0,
                  label: Text('$_activeFilterCount'),
                  backgroundColor: AppColors.coral,
                  child: SizedBox(
                    height: 54,
                    width: 54,
                    child: IconButton.filled(
                      style: IconButton.styleFrom(
                        backgroundColor: AppColors.navy,
                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
                      ),
                      onPressed: _openFilters,
                      icon: const Icon(Icons.tune_rounded),
                    ),
                  ),
                ),
              ],
            ),
          ),
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 20),
              children: [
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: const Text('All'),
                    selected: _category == null,
                    onSelected: (_) => setState(() => _category = null),
                  ),
                ),
                for (final c in DestinationCategory.values)
                  Padding(
                    padding: const EdgeInsets.only(right: 8),
                    child: ChoiceChip(
                      label: Text('${c.emoji} ${c.label}'),
                      selected: _category == c,
                      onSelected: (_) => setState(() => _category = _category == c ? null : c),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: async.when(
              loading: () => const Center(child: CircularProgressIndicator()),
              error: (e, _) => EmptyState(icon: Icons.cloud_off_rounded, title: 'Something went wrong', message: '$e'),
              data: (all) {
                final results = _apply(all);
                if (results.isEmpty) {
                  return const EmptyState(
                    icon: Icons.search_off_rounded,
                    title: 'No destinations found',
                    message: 'Try a different search or loosen your filters.',
                  );
                }
                return ListView.separated(
                  padding: const EdgeInsets.fromLTRB(20, 8, 20, 24),
                  itemCount: results.length + 1,
                  separatorBuilder: (_, _) => const SizedBox(height: 12),
                  itemBuilder: (_, i) {
                    if (i == 0) {
                      return Text('${results.length} destinations',
                          style: Theme.of(context).textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600));
                    }
                    return DestinationTile(results[i - 1]);
                  },
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}
