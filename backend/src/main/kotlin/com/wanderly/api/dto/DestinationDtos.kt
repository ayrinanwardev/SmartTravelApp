package com.wanderly.api.dto

import com.fasterxml.jackson.annotation.JsonProperty
import com.wanderly.api.domain.*
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class CostDto(val transport: Int, val hotelPerNight: Int, val foodPerDay: Int, val activitiesPerDay: Int) {
    companion object {
        fun from(c: CostEstimate) = CostDto(c.transport, c.hotelPerNight, c.foodPerDay, c.activitiesPerDay)
    }
}

data class AttractionDto(
    val id: Long?, val name: String, val type: String?, val imageUrl: String?, val rating: Double,
    val distanceKm: Double, val latitude: Double?, val longitude: Double?,
) {
    companion object {
        fun from(a: Attraction) =
            AttractionDto(a.id, a.name, a.type, a.imageUrl, a.rating, a.distanceKm, a.latitude, a.longitude)
    }
}

data class ReviewDto(val id: Long?, val author: String, val rating: Double, val comment: String?, val date: LocalDate) {
    companion object {
        fun from(r: Review) = ReviewDto(r.id, r.author, r.rating, r.comment, r.reviewDate)
    }
}

/** Lightweight card used by lists (home, explore, wishlist). */
data class DestinationSummary(
    val id: String, val name: String, val country: String, val tagline: String?, val imageUrl: String?,
    val category: DestinationCategory, val bestSeasons: List<Season>, val rating: Double, val reviewCount: Int,
    val hotelFromPerNight: Int, val typicalTripCost: Int,
    @get:JsonProperty("isTrending") val isTrending: Boolean,
    @get:JsonProperty("isPopular") val isPopular: Boolean,
) {
    companion object {
        fun from(d: Destination) = DestinationSummary(
            d.id, d.name, d.country, d.tagline, d.imageUrl, d.category, d.bestSeasons.toList(), d.rating,
            d.reviewCount, d.cost.hotelPerNight, d.typicalTripCost(), d.trending, d.popular,
        )
    }
}

/** Full payload for the Destination Details screen (and the app's catalogue load). */
data class DestinationDetail(
    val id: String, val name: String, val country: String, val tagline: String?, val description: String?,
    val imageUrl: String?, val gallery: List<String>, val category: DestinationCategory, val bestSeasons: List<Season>,
    val bestTime: String?, val rating: Double, val reviewCount: Int, val avgTempC: Int, val cost: CostDto,
    val typicalTripCost: Int, val highlights: List<String>, val attractions: List<AttractionDto>,
    val reviews: List<ReviewDto>,
    @get:JsonProperty("isTrending") val isTrending: Boolean,
    @get:JsonProperty("isPopular") val isPopular: Boolean,
    val latitude: Double?, val longitude: Double?,
) {
    companion object {
        fun from(d: Destination) = DestinationDetail(
            d.id, d.name, d.country, d.tagline, d.description, d.imageUrl, d.gallery.toList(), d.category,
            d.bestSeasons.toList(), d.bestTime, d.rating, d.reviewCount, d.avgTempC, CostDto.from(d.cost),
            d.typicalTripCost(), d.highlights.toList(), d.attractions.map(AttractionDto::from),
            d.reviews.map(ReviewDto::from), d.trending, d.popular, d.latitude, d.longitude,
        )
    }
}

data class ReviewRequest(
    @field:DecimalMin("1.0") @field:DecimalMax("5.0") val rating: Double,
    @field:NotBlank @field:Size(max = 1500) val comment: String,
)

data class WeatherDay(val date: LocalDate, val tempC: Int, val condition: String)

data class WeatherForecast(val destinationId: String, val days: List<WeatherDay>, val suggestion: String)

data class PackingSection(val title: String, val items: List<String>)

data class PackingList(val destinationId: String, val days: Int, val sections: List<PackingSection>)
