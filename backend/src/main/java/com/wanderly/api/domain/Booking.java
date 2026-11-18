package com.wanderly.api.domain;

import com.wanderly.api.domain.Enums.BookingStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
public class Booking {

    /** Confirmation code, e.g. "WND-48213977". */
    @Id
    @Column(length = 30)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    private int guests;
    private int rooms;
    private int subtotal;
    private int taxes;
    private int total;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingStatus status = BookingStatus.CONFIRMED;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Booking() {
    }

    public Booking(String id, User user, Hotel hotel, LocalDate checkIn, LocalDate checkOut,
                   int guests, int rooms, int subtotal, int taxes) {
        this.id = id;
        this.user = user;
        this.hotel = hotel;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
        this.rooms = rooms;
        this.subtotal = subtotal;
        this.taxes = taxes;
        this.total = subtotal + taxes;
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public Hotel getHotel() { return hotel; }
    public LocalDate getCheckIn() { return checkIn; }
    public LocalDate getCheckOut() { return checkOut; }
    public int getGuests() { return guests; }
    public int getRooms() { return rooms; }
    public int getSubtotal() { return subtotal; }
    public int getTaxes() { return taxes; }
    public int getTotal() { return total; }
    public BookingStatus getStatus() { return status; }
    public void setStatus(BookingStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
