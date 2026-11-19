package com.wanderly.api.service

import com.wanderly.api.domain.*
import com.wanderly.api.dto.*
import com.wanderly.api.exception.ApiException
import com.wanderly.api.repository.BookingRepository
import com.wanderly.api.repository.DestinationRepository
import com.wanderly.api.repository.HotelRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

@Service
class DestinationService(private val repo: DestinationRepository) {

    enum class Sort { RATING, PRICE_LOW, PRICE_HIGH }

    /** Filters for GET /api/destinations. All are optional. */
    data class Filter(
        val q: String? = null, val category: DestinationCategory? = null, val season: Season? = null,
        val maxBudget: Int? = null, val trending: Boolean? = null, val popular: Boolean? = null,
        val sort: Sort = Sort.RATING,
    )

    @Transactional(readOnly = true)
    fun search(f: Filter): List<DestinationSummary> = filter(f).map(DestinationSummary::from)

    @Transactional(readOnly = true)
    fun searchFull(f: Filter): List<DestinationDetail> = filter(f).map(DestinationDetail::from)

    /**
     * The catalogue is small (tens of rows), so filtering happens in memory.
     * Move to JPA Specifications + paging if it grows to thousands.
     */
    private fun filter(f: Filter): List<Destination> {
        val q = f.q?.trim()?.lowercase().orEmpty()
        val list = repo.findAll().filter { d ->
            (q.isEmpty() || d.name.lowercase().contains(q) || d.country.lowercase().contains(q) ||
                d.tagline?.lowercase()?.contains(q) == true) &&
                (f.category == null || d.category == f.category) &&
                (f.season == null || f.season in d.bestSeasons) &&
                (f.maxBudget == null || d.typicalTripCost() <= f.maxBudget) &&
                (f.trending == null || d.trending == f.trending) &&
                (f.popular == null || d.popular == f.popular)
        }
        return when (f.sort) {
            Sort.RATING -> list.sortedByDescending { it.rating }
            Sort.PRICE_LOW -> list.sortedBy { it.typicalTripCost() }
            Sort.PRICE_HIGH -> list.sortedByDescending { it.typicalTripCost() }
        }
    }

    @Transactional(readOnly = true)
    fun get(id: String) = DestinationDetail.from(find(id))

    @Transactional
    fun addReview(id: String, user: User, req: ReviewRequest): ReviewDto {
        val d = find(id)
        val review = Review(user.name, req.rating, req.comment.trim(), LocalDate.now()).also { it.user = user }
        d.addReview(review)
        repo.saveAndFlush(d)
        return ReviewDto.from(review)
    }

    /** Simulated 5-day forecast + activity suggestion (swap for a real weather API). */
    @Transactional(readOnly = true)
    fun weather(id: String): WeatherForecast {
        val d = find(id)
        val seed = d.id.sumOf { it.code }
        var rainy = 0
        val days = (0 until 5).map { i ->
            val temp = d.avgTempC + ((seed + i * 5) % 7) - 3
            val rain = (seed + i * 3) % 5 == 0
            val cloudy = !rain && (seed + i) % 3 == 0
            if (rain) rainy++
            WeatherDay(LocalDate.now().plusDays(i.toLong()), temp, if (rain) "rain" else if (cloudy) "cloudy" else "sunny")
        }
        val suggestion = when {
            rainy >= 2 -> "Rain expected on $rainy days — plan museums, cafés and spa time for wet afternoons."
            d.avgTempC >= 26 -> "Hot & sunny — do outdoor sightseeing before 11am and save sunsets for the beach."
            d.avgTempC <= 15 -> "Cool weather — perfect for hiking and city walks. Pack warm layers."
            else -> "Pleasant conditions — great for outdoor activities all day."
        }
        return WeatherForecast(d.id, days, suggestion)
    }

    fun find(id: String): Destination = repo.findById(id).orElseThrow { ApiException.notFound("Destination", id) }
}

@Service
class HotelService(
    private val hotels: HotelRepository,
    private val bookings: BookingRepository,
    private val notifications: NotificationService,
) {
    companion object {
        const val TAX_RATE = 0.10
        private val RANDOM = SecureRandom()
    }

    @Transactional(readOnly = true)
    fun list(destinationId: String?, cheapestFirst: Boolean): List<HotelDto> {
        val list = if (destinationId.isNullOrBlank()) hotels.findAllByOrderByRatingDesc()
        else hotels.findByDestinationIdOrderByRatingDesc(destinationId)
        return (if (cheapestFirst) list.sortedBy { it.pricePerNight } else list).map(HotelDto::from)
    }

    @Transactional(readOnly = true)
    fun get(id: String) = HotelDto.from(find(id))

    /** Reserves the stay; it is confirmed once paid (see PaymentService). */
    @Transactional
    fun book(user: User, req: BookingRequest): BookingDto {
        if (!req.checkOut.isAfter(req.checkIn)) throw ApiException.badRequest("Check-out must be after check-in")
        val hotel = find(req.hotelId)
        val nights = ChronoUnit.DAYS.between(req.checkIn, req.checkOut)
        if (nights > 30) throw ApiException.badRequest("Bookings are limited to 30 nights")

        val subtotal = Math.toIntExact(hotel.pricePerNight * nights * req.rooms)
        val taxes = (subtotal * TAX_RATE).roundToInt()
        var code: String
        do {
            code = "WND-${10_000_000 + RANDOM.nextInt(90_000_000)}"
        } while (bookings.existsById(code))

        val booking = bookings.save(Booking(code, user, hotel, req.checkIn, req.checkOut, req.guests, req.rooms, subtotal, taxes))
        notifications.notify(
            user, NotificationType.BOOKING, "Booking reserved: ${hotel.name}",
            "Check-in ${req.checkIn.format(DateTimeFormatter.ofPattern("d MMM yyyy"))} · " +
                "pay ৳${booking.total} to confirm · ref $code",
        )
        return BookingDto.from(booking)
    }

    @Transactional(readOnly = true)
    fun myBookings(user: User) = bookings.findByUserIdOrderByCreatedAtDesc(user.uid).map(BookingDto::from)

    @Transactional(readOnly = true)
    fun getBooking(user: User, id: String) = BookingDto.from(
        bookings.findByIdAndUserId(id, user.uid).orElseThrow { ApiException.notFound("Booking", id) }
    )

    @Transactional
    fun cancel(user: User, bookingId: String): BookingDto {
        val b = bookings.findByIdAndUserId(bookingId, user.uid).orElseThrow { ApiException.notFound("Booking", bookingId) }
        if (b.status == BookingStatus.CANCELLED) throw ApiException.conflict("Booking is already cancelled")
        b.status = BookingStatus.CANCELLED
        return BookingDto.from(b)
    }

    private fun find(id: String): Hotel = hotels.findById(id).orElseThrow { ApiException.notFound("Hotel", id) }
}
