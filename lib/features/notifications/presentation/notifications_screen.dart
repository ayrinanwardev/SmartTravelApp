import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:material_ui/material_ui.dart';

import '../../../core/theme/app_colors.dart';
import '../../../core/utils/formatters.dart';
import '../../../core/widgets/common.dart';
import '../providers/notifications_provider.dart';

class NotificationsScreen extends ConsumerWidget {
  const NotificationsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final items = ref.watch(notificationsProvider);
    final unread = ref.watch(unreadNotificationsProvider);
    final live = ref.watch(realtimeProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications'),
        actions: [
          if (live != null)
            ValueListenableBuilder<bool>(
              valueListenable: live.connected,
              builder: (_, on, _) => Padding(
                padding: const EdgeInsets.only(right: 4),
                child: Chip(
                  avatar: Icon(Icons.circle, size: 10, color: on ? AppColors.success : AppColors.textSecondary),
                  label: Text(on ? 'Live' : 'Offline', style: const TextStyle(fontSize: 12)),
                  visualDensity: VisualDensity.compact,
                ),
              ),
            ),
          if (unread > 0)
            TextButton(
              onPressed: () => runApi(context, ref.read(notificationsProvider.notifier).markAllRead()),
              child: const Text('Mark all read'),
            ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => ref.read(notificationsProvider.notifier).refresh(),
        child: items.isEmpty
            ? ListView(children: const [
                SizedBox(height: 120),
                EmptyState(
                  icon: Icons.notifications_none_rounded,
                  title: 'No notifications yet',
                  message: 'Payments, companions joining your trips and budget alerts show up here instantly.',
                ),
              ])
            : ListView.separated(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
                itemCount: items.length,
                separatorBuilder: (_, _) => const SizedBox(height: 8),
                itemBuilder: (_, i) {
                  final n = items[i];
                  return Card(
                    color: n.read ? null : AppColors.coral.withValues(alpha: 0.06),
                    child: ListTile(
                      leading: IconBubble(icon: notificationIcon(n.type), color: n.read ? AppColors.textSecondary : AppColors.coral),
                      title: Text(n.title, style: TextStyle(fontWeight: n.read ? FontWeight.w500 : FontWeight.w700)),
                      subtitle: Text('${n.message}\n${Fmt.weekday(n.createdAt.toLocal())}'),
                      isThreeLine: true,
                      onTap: () {
                        if (!n.read) ref.read(notificationsProvider.notifier).markRead(n.id);
                        if (n.type == 'booking') context.push('/bookings');
                        if (n.type == 'trip') context.go('/trips');
                        if (n.type == 'budget') context.push('/budget');
                      },
                    ),
                  );
                },
              ),
      ),
    );
  }
}

/// Bell icon with an unread badge, used in the Home header.
class NotificationBell extends ConsumerWidget {
  const NotificationBell({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final unread = ref.watch(unreadNotificationsProvider);
    return Badge(
      isLabelVisible: unread > 0,
      label: Text(unread > 9 ? '9+' : '$unread'),
      backgroundColor: AppColors.coral,
      child: IconButton.filledTonal(
        tooltip: 'Notifications',
        onPressed: () => context.push('/notifications'),
        icon: const Icon(Icons.notifications_none_rounded),
      ),
    );
  }
}
