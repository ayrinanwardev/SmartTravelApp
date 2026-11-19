package com.wanderly.api.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Entity
@Table(name = "trips")
class Trip(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    var user: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "destination_id")
    var destination: Destination,
    @Column(nullable = false, length = 150) var title: String,
    @Column(name = "start_date", nullable = false) var startDate: LocalDate,
    @Column(name = "end_date", nullable = false) var endDate: LocalDate,
    var travelers: Int,
    var budget: Int,
) {
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: String? = null

    @Column(name = "image_url", length = 500) var imageUrl: String? = destination.imageUrl

    /** Invite code companions use to join this trip (created on first share). */
    @Column(name = "share_code", unique = true, length = 8) var shareCode: String? = null

    @OneToMany(mappedBy = "trip", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    var days: MutableList<ItineraryDay> = mutableListOf()

    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()

    val tripId: String get() = requireNotNull(id) { "Trip is not persisted yet" }

    fun dayCount(): Int = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

    /** Grows or shrinks the itinerary to exactly one day per date, keeping existing days. */
    fun syncDays() {
        val count = dayCount()
        days.removeIf { it.dayNumber > count }
        for (n in days.size + 1..count) days += ItineraryDay(this, n)
    }

    fun day(dayNumber: Int): ItineraryDay? = days.firstOrNull { it.dayNumber == dayNumber }
}

@Entity
@Table(name = "itinerary_days", uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "day_number"])])
class ItineraryDay(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id")
    var trip: Trip,
    @Column(name = "day_number", nullable = false) var dayNumber: Int,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null

    /** List order is the order shown in the app (drag-to-reorder). */
    @OneToMany(mappedBy = "day", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderColumn(name = "position")
    var activities: MutableList<Activity> = mutableListOf()

    fun addActivity(a: Activity) {
        a.day = this
        activities += a
    }
}

@Entity
@Table(name = "activities")
class Activity(
    @Column(nullable = false, length = 200) var title: String,
    /** "HH:mm", e.g. "09:30". */
    @Column(name = "start_time", nullable = false, length = 5) var time: String,
    @Enumerated(EnumType.STRING) @Column(name = "activity_type", nullable = false, length = 20) var type: ActivityType,
    @Column(length = 500) var note: String = "",
    var cost: Int = 0,
) {
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: String? = null

    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "day_id")
    var day: ItineraryDay? = null

    fun update(title: String, time: String, type: ActivityType, note: String?, cost: Int) {
        this.title = title
        this.time = time
        this.type = type
        this.note = note.orEmpty()
        this.cost = cost
    }
}

/** A companion who joined someone else's trip with its share code. The owner is not stored here. */
@Entity
@Table(name = "trip_members", uniqueConstraints = [UniqueConstraint(columnNames = ["trip_id", "user_id"])])
class TripMember(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "trip_id") var trip: Trip,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @Column(name = "joined_at", nullable = false) var joinedAt: Instant = Instant.now()
}
