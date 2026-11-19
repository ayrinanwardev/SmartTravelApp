package com.wanderly.api.domain

import jakarta.persistence.*
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

/** Typical per-person costs in BDT. */
@Embeddable
class CostEstimate(
    @Column(name = "cost_transport") var transport: Int = 0,
    @Column(name = "cost_hotel_per_night") var hotelPerNight: Int = 0,
    @Column(name = "cost_food_per_day") var foodPerDay: Int = 0,
    @Column(name = "cost_activities_per_day") var activitiesPerDay: Int = 0,
) {
    /** Same formula as the Flutter app: return transport + nights of hotel + daily spend. */
    fun forDays(days: Int, travelers: Int): Int {
        val nights = max(1, days - 1)
        return transport * travelers + hotelPerNight * nights + (foodPerDay + activitiesPerDay) * days * travelers
    }
}

@Entity
@Table(name = "destinations")
class Destination(
    /** Human-readable slug, e.g. "coxs-bazar" — the same ids the Flutter app uses. */
    @Id @Column(length = 60) var id: String,
    @Column(nullable = false, length = 100) var name: String,
    @Column(nullable = false, length = 80) var country: String,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var category: DestinationCategory,
) {
    @Column(length = 200) var tagline: String? = null
    @Column(length = 3000) var description: String? = null
    @Column(name = "image_url", length = 500) var imageUrl: String? = null

    @ElementCollection
    @CollectionTable(name = "destination_gallery", joinColumns = [JoinColumn(name = "destination_id")])
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500)
    var gallery: MutableList<String> = mutableListOf()

    @ElementCollection
    @CollectionTable(name = "destination_seasons", joinColumns = [JoinColumn(name = "destination_id")])
    @Enumerated(EnumType.STRING)
    @Column(name = "season", length = 20)
    var bestSeasons: MutableSet<Season> = linkedSetOf()

    @Column(name = "best_time", length = 80) var bestTime: String? = null
    var rating: Double = 0.0
    @Column(name = "review_count") var reviewCount: Int = 0
    @Column(name = "avg_temp_c") var avgTempC: Int = 0

    @Embedded var cost: CostEstimate = CostEstimate()

    @ElementCollection
    @CollectionTable(name = "destination_highlights", joinColumns = [JoinColumn(name = "destination_id")])
    @OrderColumn(name = "position")
    @Column(name = "highlight", length = 120)
    var highlights: MutableList<String> = mutableListOf()

    @OneToMany(mappedBy = "destination", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("rating DESC")
    var attractions: MutableList<Attraction> = mutableListOf()

    @OneToMany(mappedBy = "destination", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("reviewDate DESC")
    var reviews: MutableList<Review> = mutableListOf()

    var trending: Boolean = false
    var popular: Boolean = false

    /** Map centre of the destination. */
    var latitude: Double? = null
    var longitude: Double? = null

    /** Per-person estimate for a typical 3-day trip (used by budget filters). */
    fun typicalTripCost(): Int = cost.forDays(3, 1)

    fun addAttraction(a: Attraction) {
        a.destination = this
        attractions += a
    }

    fun addReview(r: Review) {
        r.destination = this
        reviews += r
        val total = rating * reviewCount + r.rating
        reviewCount++
        rating = (total / reviewCount * 10).roundToInt() / 10.0
    }
}

@Entity
@Table(name = "attractions")
class Attraction(
    @Column(nullable = false, length = 120) var name: String,
    @Column(name = "attraction_type", length = 60) var type: String?,
    @Column(name = "image_url", length = 500) var imageUrl: String?,
    var rating: Double = 0.0,
    @Column(name = "distance_km") var distanceKm: Double = 0.0,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    var destination: Destination? = null

    var latitude: Double? = null
    var longitude: Double? = null
}

@Entity
@Table(name = "reviews")
class Review(
    @Column(nullable = false, length = 100) var author: String,
    var rating: Double,
    @Column(name = "review_comment", length = 1500) var comment: String?,
    @Column(name = "review_date", nullable = false) var reviewDate: LocalDate,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    var destination: Destination? = null

    /** Null for seeded reviews. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    var user: User? = null
}

@Entity
@Table(name = "hotels")
class Hotel(
    @Id @Column(length = 60) var id: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "destination_id")
    var destination: Destination,
    @Column(nullable = false, length = 150) var name: String,
    @Column(length = 250) var address: String?,
    @Column(name = "price_per_night", nullable = false) var pricePerNight: Int,
    var rating: Double,
    @Column(name = "review_count") var reviewCount: Int,
    var stars: Int,
    @Column(length = 1500) var description: String?,
) {
    @ElementCollection
    @CollectionTable(name = "hotel_images", joinColumns = [JoinColumn(name = "hotel_id")])
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500)
    var imageUrls: MutableList<String> = mutableListOf()

    @ElementCollection
    @CollectionTable(name = "hotel_amenities", joinColumns = [JoinColumn(name = "hotel_id")])
    @OrderColumn(name = "position")
    @Column(name = "amenity", length = 80)
    var amenities: MutableList<String> = mutableListOf()

    var latitude: Double? = null
    var longitude: Double? = null
}
