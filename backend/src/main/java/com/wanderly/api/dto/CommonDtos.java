package com.wanderly.api.dto;

import com.wanderly.api.domain.*;
import com.wanderly.api.domain.Enums.BookingStatus;
import com.wanderly.api.domain.Enums.ExpenseCategory;
import com.wanderly.api.domain.Enums.NotificationType;
import jakarta.validation.constraints.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DTOs for auth, users, hotels, bookings, expenses, journal and notifications. */
public final class CommonDtos {

    private CommonDtos() {
    }

    // ---------- Auth & users ----------

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 100) String name,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record UserDto(Long id, String name, String email, int budgetTotal, boolean notificationsEnabled) {
        public static UserDto from(User u) {
            return new UserDto(u.getId(), u.getName(), u.getEmail(), u.getBudgetTotal(), u.isNotificationsEnabled());
        }
    }

    public record AuthResponse(String token, String tokenType, long expiresInSeconds, UserDto user) {
    }

    public record UpdateProfileRequest(@Size(min = 2, max = 100) String name, Boolean notificationsEnabled) {
    }

    public record ProfileStats(long trips, long saved, long memories, long bookings, long unreadNotifications) {
    }

    // ---------- Hotels & bookings ----------

    public record HotelDto(String id, String destinationId, String name, String address, List<String> imageUrls,
                           int pricePerNight, double rating, int reviewCount, List<String> amenities,
                           String description, int stars) {
        public static HotelDto from(Hotel h) {
            return new HotelDto(h.getId(), h.getDestination().getId(), h.getName(), h.getAddress(),
                    List.copyOf(h.getImageUrls()), h.getPricePerNight(), h.getRating(), h.getReviewCount(),
                    List.copyOf(h.getAmenities()), h.getDescription(), h.getStars());
        }
    }

    public record BookingRequest(
            @NotBlank String hotelId,
            @NotNull @FutureOrPresent LocalDate checkIn,
            @NotNull @Future LocalDate checkOut,
            @Min(1) @Max(20) int guests,
            @Min(1) @Max(10) int rooms) {
    }

    public record BookingDto(String id, String hotelId, String hotelName, String imageUrl, LocalDate checkIn,
                             LocalDate checkOut, int nights, int guests, int rooms, int subtotal, int taxes, int total,
                             BookingStatus status, Instant createdAt) {
        public static BookingDto from(Booking b) {
            var h = b.getHotel();
            int nights = (int) (b.getCheckOut().toEpochDay() - b.getCheckIn().toEpochDay());
            return new BookingDto(b.getId(), h.getId(), h.getName(), h.getImageUrls().isEmpty() ? null : h.getImageUrls().getFirst(),
                    b.getCheckIn(), b.getCheckOut(), nights, b.getGuests(), b.getRooms(), b.getSubtotal(),
                    b.getTaxes(), b.getTotal(), b.getStatus(), b.getCreatedAt());
        }
    }

    // ---------- Budget ----------

    public record ExpenseRequest(
            @NotBlank @Size(max = 150) String title,
            @Positive int amount,
            @NotNull ExpenseCategory category,
            LocalDate date,
            String tripId) {
    }

    public record ExpenseDto(String id, String title, int amount, ExpenseCategory category, LocalDate date, String tripId) {
        public static ExpenseDto from(Expense e) {
            return new ExpenseDto(e.getId(), e.getTitle(), e.getAmount(), e.getCategory(), e.getDate(),
                    e.getTrip() == null ? null : e.getTrip().getId());
        }
    }

    public record BudgetUpdateRequest(@Positive int total) {
    }

    public record BudgetSummary(int total, int spent, int remaining, double usedFraction, boolean isOver,
                                boolean isNearLimit, Map<ExpenseCategory, Integer> byCategory, String alert) {
    }

    // ---------- Journal ----------

    public record JournalRequest(
            @NotBlank @Size(max = 150) String title,
            @Size(max = 5000) String body,
            @Size(max = 150) String location,
            @NotNull LocalDate date,
            @Size(max = 100) String album,
            List<String> photoUrls) {
    }

    public record JournalDto(String id, String title, String body, String location, LocalDate date, String album,
                             List<String> photoUrls) {
        public static JournalDto from(JournalEntry j) {
            return new JournalDto(j.getId(), j.getTitle(), j.getBody(), j.getLocation(), j.getDate(), j.getAlbum(),
                    List.copyOf(j.getPhotoUrls()));
        }
    }

    public record UploadResponse(String url) {
    }

    // ---------- Notifications ----------

    public record NotificationDto(Long id, String title, String message, NotificationType type, boolean read,
                                  Instant createdAt) {
        public static NotificationDto from(Notification n) {
            return new NotificationDto(n.getId(), n.getTitle(), n.getMessage(), n.getType(), n.isRead(), n.getCreatedAt());
        }
    }

    // ---------- Tools ----------

    public record ConversionResult(String from, String to, double amount, double result, double rate) {
    }
}
