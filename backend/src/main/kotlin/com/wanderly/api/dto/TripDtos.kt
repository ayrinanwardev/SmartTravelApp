package com.wanderly.api.dto

import com.fasterxml.jackson.annotation.JsonIgnore
import com.wanderly.api.domain.Activity
import com.wanderly.api.domain.ActivityType
import com.wanderly.api.domain.ItineraryDay
import com.wanderly.api.domain.Trip
import jakarta.validation.Valid
import jakarta.validation.constraints.*
import java.time.Instant
import java.time.LocalDate

/* JSON shapes mirror the Flutter `Trip`, `ItineraryDay` and `Activity` models. */

data class ActivityDto(val id: String?, val title: String, val time: String, val type: ActivityType, val note: String, val cost: Int) {
    companion object {
        fun from(a: Activity) = ActivityDto(a.id, a.title, a.time, a.type, a.note, a.cost)
    }
}

data class DayDto(val dayNumber: Int, val date: LocalDate, val activities: List<ActivityDto>) {
    companion object {
        fun from(d: ItineraryDay, start: LocalDate) =
            DayDto(d.dayNumber, start.plusDays(d.dayNumber - 1L), d.activities.map(ActivityDto::from))
    }
}

/**
 * [role] is the viewer's role ("owner" or "member"); [shareCode] is only returned to the owner.
 * [memberCount] includes the owner.
 */
data class TripResponse(
    val id: String, val destinationId: String, val destinationName: String, val title: String, val imageUrl: String?,
    val startDate: LocalDate, val endDate: LocalDate, val dayCount: Int, val travelers: Int, val budget: Int,
    val plannedCost: Int, val activityCount: Int, val upcoming: Boolean, val days: List<DayDto>,
    val role: String, val ownerName: String, val memberCount: Int, val shareCode: String?,
) {
    companion object {
        fun from(t: Trip, viewerId: Long, companions: Int): TripResponse {
            val days = t.days.map { DayDto.from(it, t.startDate) }
            val owner = t.user.id == viewerId
            return TripResponse(
                t.tripId, t.destination.id, t.destination.name, t.title, t.imageUrl, t.startDate, t.endDate,
                t.dayCount(), t.travelers, t.budget,
                plannedCost = days.sumOf { d -> d.activities.sumOf { it.cost } },
                activityCount = days.sumOf { it.activities.size },
                upcoming = !t.endDate.isBefore(LocalDate.now()),
                days = days,
                role = if (owner) "owner" else "member",
                ownerName = t.user.name,
                memberCount = companions + 1,
                shareCode = if (owner) t.shareCode else null,
            )
        }
    }
}

data class ActivityRequest(
    @field:NotBlank @field:Size(max = 200) val title: String,
    @field:NotBlank @field:Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm") val time: String,
    val type: ActivityType,
    @field:Size(max = 500) val note: String? = null,
    @field:PositiveOrZero val cost: Int? = null,
) {
    @get:JsonIgnore val costOrZero: Int get() = cost ?: 0
}

data class DayRequest(@field:Min(1) val dayNumber: Int, val activities: List<@Valid ActivityRequest>? = null)

/** [days] is optional — the trip assistant sends a full itinerary, the manual planner sends none. */
data class CreateTripRequest(
    @field:NotBlank val destinationId: String,
    @field:Size(max = 150) val title: String? = null,
    val startDate: LocalDate,
    val endDate: LocalDate,
    @field:Min(1) @field:Max(50) val travelers: Int,
    @field:PositiveOrZero val budget: Int? = null,
    val days: List<@Valid DayRequest>? = null,
)

data class UpdateTripRequest(
    @field:NotBlank @field:Size(max = 150) val title: String,
    val startDate: LocalDate,
    val endDate: LocalDate,
    @field:Min(1) @field:Max(50) val travelers: Int,
    @field:PositiveOrZero val budget: Int,
)

/** New order of activity ids for one day (drag-to-reorder). */
data class ReorderRequest(val activityIds: List<String>)

data class JoinTripRequest(@field:NotBlank @field:Size(min = 6, max = 8) val code: String)

data class ShareCodeResponse(val code: String)

data class MemberDto(val userId: Long, val name: String, val role: String, val online: Boolean)

/** Live position shared by a trip member over the WebSocket. */
data class LocationDto(
    val tripId: String, val userId: Long, val name: String, val lat: Double, val lng: Double,
    val accuracy: Double?, val at: Instant,
)
