package com.wanderly.api.service;

import com.wanderly.api.domain.Booking;
import com.wanderly.api.domain.Enums.BookingStatus;
import com.wanderly.api.domain.Enums.NotificationType;
import com.wanderly.api.domain.Hotel;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.BookingDto;
import com.wanderly.api.dto.CommonDtos.BookingRequest;
import com.wanderly.api.dto.CommonDtos.HotelDto;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.BookingRepository;
import com.wanderly.api.repository.HotelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;

@Service
public class HotelService {

    static final double TAX_RATE = 0.10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final HotelRepository hotels;
    private final BookingRepository bookings;
    private final NotificationService notifications;

    public HotelService(HotelRepository hotels, BookingRepository bookings, NotificationService notifications) {
        this.hotels = hotels;
        this.bookings = bookings;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<HotelDto> list(String destinationId, boolean cheapestFirst) {
        var list = destinationId == null || destinationId.isBlank()
                ? hotels.findAllByOrderByRatingDesc()
                : hotels.findByDestinationIdOrderByRatingDesc(destinationId);
        var stream = list.stream();
        if (cheapestFirst) stream = stream.sorted(Comparator.comparingInt(Hotel::getPricePerNight));
        return stream.map(HotelDto::from).toList();
    }

    @Transactional(readOnly = true)
    public HotelDto get(String id) {
        return HotelDto.from(find(id));
    }

    @Transactional
    public BookingDto book(User user, BookingRequest req) {
        if (!req.checkOut().isAfter(req.checkIn())) {
            throw ApiException.badRequest("Check-out must be after check-in");
        }
        Hotel hotel = find(req.hotelId());
        long nights = ChronoUnit.DAYS.between(req.checkIn(), req.checkOut());
        if (nights > 30) throw ApiException.badRequest("Bookings are limited to 30 nights");

        int subtotal = Math.toIntExact(hotel.getPricePerNight() * nights * req.rooms());
        int taxes = (int) Math.round(subtotal * TAX_RATE);
        String code;
        do {
            code = "WND-" + (10_000_000 + RANDOM.nextInt(90_000_000));
        } while (bookings.existsById(code));

        var booking = bookings.save(new Booking(code, user, hotel, req.checkIn(), req.checkOut(),
                req.guests(), req.rooms(), subtotal, taxes));
        notifications.notify(user, NotificationType.BOOKING, "Booking confirmed: " + hotel.getName(),
                "Check-in " + req.checkIn().format(DateTimeFormatter.ofPattern("d MMM yyyy")) + " · confirmation " + code);
        return BookingDto.from(booking);
    }

    @Transactional(readOnly = true)
    public List<BookingDto> myBookings(User user) {
        return bookings.findByUserIdOrderByCreatedAtDesc(user.getId()).stream().map(BookingDto::from).toList();
    }

    @Transactional
    public BookingDto cancel(User user, String bookingId) {
        var b = bookings.findByIdAndUserId(bookingId, user.getId())
                .orElseThrow(() -> ApiException.notFound("Booking", bookingId));
        if (b.getStatus() == BookingStatus.CANCELLED) throw ApiException.conflict("Booking is already cancelled");
        b.setStatus(BookingStatus.CANCELLED);
        return BookingDto.from(b);
    }

    private Hotel find(String id) {
        return hotels.findById(id).orElseThrow(() -> ApiException.notFound("Hotel", id));
    }
}
