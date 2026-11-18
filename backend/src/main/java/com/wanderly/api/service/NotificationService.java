package com.wanderly.api.service;

import com.wanderly.api.domain.Enums.NotificationType;
import com.wanderly.api.domain.Notification;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.NotificationDto;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.NotificationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * In-app notifications (trip created, booking confirmed, budget alerts).
 * A Firebase Cloud Messaging sender can be added in {@link #notify} to also push to devices.
 */
@Service
public class NotificationService {

    private final NotificationRepository repo;

    public NotificationService(NotificationRepository repo) {
        this.repo = repo;
    }

    @Transactional
    public void notify(User user, NotificationType type, String title, String message) {
        if (!user.isNotificationsEnabled() && type != NotificationType.SYSTEM) return;
        repo.save(new Notification(user, type, title, message));
    }

    @Transactional(readOnly = true)
    public List<NotificationDto> list(User user) {
        return repo.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(NotificationDto::from).toList();
    }

    @Transactional
    public NotificationDto markRead(User user, Long id) {
        var n = repo.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiException.notFound("Notification", id));
        n.markRead();
        return NotificationDto.from(n);
    }

    @Transactional
    public void markAllRead(User user) {
        repo.findByUserIdOrderByCreatedAtDesc(user.getId()).forEach(Notification::markRead);
    }
}
