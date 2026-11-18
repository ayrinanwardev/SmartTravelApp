package com.wanderly.api.service;

import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.ProfileStats;
import com.wanderly.api.dto.CommonDtos.UpdateProfileRequest;
import com.wanderly.api.dto.CommonDtos.UserDto;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;
    private final TripRepository trips;
    private final WishlistRepository wishlist;
    private final JournalRepository journal;
    private final BookingRepository bookings;
    private final NotificationRepository notifications;

    public UserService(UserRepository users, TripRepository trips, WishlistRepository wishlist,
                       JournalRepository journal, BookingRepository bookings, NotificationRepository notifications) {
        this.users = users;
        this.trips = trips;
        this.wishlist = wishlist;
        this.journal = journal;
        this.bookings = bookings;
        this.notifications = notifications;
    }

    /** Loads a fresh, managed copy of the authenticated user. */
    @Transactional(readOnly = true)
    public User load(User principal) {
        return users.findById(principal.getId()).orElseThrow(() -> ApiException.notFound("User", principal.getId()));
    }

    @Transactional(readOnly = true)
    public UserDto me(User principal) {
        return UserDto.from(load(principal));
    }

    @Transactional
    public UserDto update(User principal, UpdateProfileRequest req) {
        User u = load(principal);
        if (req.name() != null && !req.name().isBlank()) u.setName(req.name().trim());
        if (req.notificationsEnabled() != null) u.setNotificationsEnabled(req.notificationsEnabled());
        return UserDto.from(users.save(u));
    }

    @Transactional(readOnly = true)
    public ProfileStats stats(User principal) {
        Long id = principal.getId();
        return new ProfileStats(trips.countByUserId(id), wishlist.countByUserId(id), journal.countByUserId(id),
                bookings.countByUserId(id), notifications.countByUserIdAndReadFalse(id));
    }
}
