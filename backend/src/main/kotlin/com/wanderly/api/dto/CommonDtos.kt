package com.wanderly.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.wanderly.api.domain.*
import jakarta.validation.constraints.*
import java.time.Instant
import java.time.LocalDate

// ---------- Auth & users ----------

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 2, max = 100) val name: String,
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank @field:Size(min = 6, max = 100) val password: String,
)

data class LoginRequest(@field:NotBlank @field:Email val email: String, @field:NotBlank val password: String)

data class UserDto(val id: Long, val name: String, val email: String, val budgetTotal: Int, val notificationsEnabled: Boolean) {
    companion object {
        fun from(u: User) = UserDto(u.uid, u.name, u.email, u.budgetTotal, u.notificationsEnabled)
    }
}

data class AuthResponse(val token: String, val tokenType: String, val expiresInSeconds: Long, val user: UserDto)

data class UpdateProfileRequest(@field:Size(min = 2, max = 100) val name: String? = null, val notificationsEnabled: Boolean? = null)

data class ProfileStats(val trips: Long, val saved: Long, val memories: Long, val bookings: Long, val unreadNotifications: Long)

// ---------- Hotels & bookings ----------

data class HotelDto(
    val id: String, val destinationId: String, val name: String, val address: String?, val imageUrls: List<String>,
    val pricePerNight: Int, val rating: Double, val reviewCount: Int, val amenities: List<String>,
    val description: String?, val stars: Int, val latitude: Double?, val longitude: Double?,
) {
    companion object {
        fun from(h: Hotel) = HotelDto(
            h.id, h.destination.id, h.name, h.address, h.imageUrls.toList(), h.pricePerNight, h.rating,
            h.reviewCount, h.amenities.toList(), h.description, h.stars, h.latitude, h.longitude,
        )
    }
}

data class BookingRequest(
    @field:NotBlank val hotelId: String,
    @field:FutureOrPresent val checkIn: LocalDate,
    @field:Future val checkOut: LocalDate,
    @field:Min(1) @field:Max(20) val guests: Int,
    @field:Min(1) @field:Max(10) val rooms: Int,
)

data class BookingDto(
    val id: String, val hotelId: String, val hotelName: String, val imageUrl: String?, val checkIn: LocalDate,
    val checkOut: LocalDate, val nights: Int, val guests: Int, val rooms: Int, val subtotal: Int, val taxes: Int,
    val total: Int, val status: BookingStatus, val createdAt: Instant, val paymentStatus: PaymentStatus,
    val paymentMethod: String?, val paidAt: Instant?,
) {
    companion object {
        fun from(b: Booking) = BookingDto(
            b.id, b.hotel.id, b.hotel.name, b.hotel.imageUrls.firstOrNull(), b.checkIn, b.checkOut,
            (b.checkOut.toEpochDay() - b.checkIn.toEpochDay()).toInt(), b.guests, b.rooms, b.subtotal, b.taxes,
            b.total, b.status, b.createdAt, b.paymentStatus, b.paymentMethod, b.paidAt,
        )
    }
}

// ---------- Payments ----------

data class PaymentSessionDto(val bookingId: String, val tranId: String, val gatewayUrl: String, val amount: Int, val currency: String)

// ---------- Budget ----------

data class ExpenseRequest(
    @field:NotBlank @field:Size(max = 150) val title: String,
    @field:Positive val amount: Int,
    val category: ExpenseCategory,
    val date: LocalDate? = null,
    val tripId: String? = null,
)

data class ExpenseDto(val id: String?, val title: String, val amount: Int, val category: ExpenseCategory, val date: LocalDate, val tripId: String?) {
    companion object {
        fun from(e: Expense) = ExpenseDto(e.id, e.title, e.amount, e.category, e.date, e.trip?.id)
    }
}

data class BudgetUpdateRequest(@field:Positive val total: Int)

data class BudgetSummary(
    val total: Int, val spent: Int, val remaining: Int, val usedFraction: Double,
    @get:JsonProperty("isOver") val isOver: Boolean,
    @get:JsonProperty("isNearLimit") val isNearLimit: Boolean,
    val byCategory: Map<ExpenseCategory, Int>, val alert: String?,
)

// ---------- Journal ----------

data class JournalRequest(
    @field:NotBlank @field:Size(max = 150) val title: String,
    @field:Size(max = 5000) val body: String? = null,
    @field:Size(max = 150) val location: String? = null,
    val date: LocalDate,
    @field:Size(max = 100) val album: String? = null,
    val photoUrls: List<String>? = null,
)

data class JournalDto(val id: String?, val title: String, val body: String?, val location: String?, val date: LocalDate, val album: String, val photoUrls: List<String>) {
    companion object {
        fun from(j: JournalEntry) = JournalDto(j.id, j.title, j.body, j.location, j.date, j.album, j.photoUrls.toList())
    }
}

data class UploadResponse(val url: String)

// ---------- Notifications & devices ----------

data class NotificationDto(val id: Long?, val title: String, val message: String?, val type: NotificationType, val read: Boolean, val createdAt: Instant) {
    companion object {
        fun from(n: Notification) = NotificationDto(n.id, n.title, n.message, n.type, n.read, n.createdAt)
    }
}

data class DeviceRequest(@field:NotBlank @field:Size(max = 512) val token: String, @field:Size(max = 20) val platform: String? = null)

// ---------- Tools ----------

data class ConversionResult(val from: String, val to: String, val amount: Double, val result: Double, val rate: Double)
