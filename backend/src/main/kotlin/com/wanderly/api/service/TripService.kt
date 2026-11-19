package com.wanderly.api.service

import com.wanderly.api.domain.*
import com.wanderly.api.dto.*
import com.wanderly.api.exception.ApiException
import com.wanderly.api.realtime.RealtimeHub
import com.wanderly.api.realtime.RealtimePublisher
import com.wanderly.api.realtime.TripAccess
import com.wanderly.api.repository.TripMemberRepository
import com.wanderly.api.repository.TripRepository
import com.wanderly.api.repository.UserRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Trips, itineraries and group trips. The owner and companions who joined with the share
 * code can view and edit the itinerary; only the owner can share or delete the trip.
 * Every change is pushed to all participants as a `trip.updated` event.
 */
@Service
class TripService(
    private val trips: TripRepository,
    private val members: TripMemberRepository,
    private val users: UserRepository,
    private val destinations: DestinationService,
    private val notifications: NotificationService,
    private val realtime: RealtimePublisher,
    private val hub: RealtimeHub,
    private val access: TripAccess,
) {
    companion object {
        private const val MAX_DAYS = 60
        private const val CODE_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I
        private val RANDOM = SecureRandom()
    }

    @Transactional(readOnly = true)
    fun list(user: User, status: String?): List<TripResponse> {
        val today = LocalDate.now()
        return trips.findAccessible(user.uid)
            .filter {
                status == null ||
                    (status.equals("upcoming", true) && !it.endDate.isBefore(today)) ||
                    (status.equals("past", true) && it.endDate.isBefore(today))
            }
            .map { view(it, user) }
    }

    @Transactional(readOnly = true)
    fun get(user: User, id: String) = view(find(user, id), user)

    @Transactional
    fun create(user: User, req: CreateTripRequest): TripResponse {
        validateDates(req.startDate, req.endDate)
        val dest = destinations.find(req.destinationId)
        val days = ChronoUnit.DAYS.between(req.startDate, req.endDate).toInt() + 1
        val budget = req.budget ?: dest.cost.forDays(days, req.travelers)
        val title = req.title?.takeIf { it.isNotBlank() }?.trim() ?: "${dest.name} Trip"

        val trip = Trip(user, dest, title, req.startDate, req.endDate, req.travelers, budget).apply { syncDays() }
        req.days?.forEach { d ->
            val day = trip.day(d.dayNumber) ?: return@forEach
            d.activities?.forEach { day.addActivity(it.toActivity()) }
        }
        val saved = trips.save(trip)
        notifications.notify(
            user, NotificationType.TRIP, "Trip created: $title",
            "$days-day trip to ${dest.name} starting ${req.startDate}.",
        )
        return view(saved, user)
    }

    @Transactional
    fun update(user: User, id: String, req: UpdateTripRequest): TripResponse {
        validateDates(req.startDate, req.endDate)
        val trip = find(user, id).apply {
            title = req.title.trim()
            startDate = req.startDate
            endDate = req.endDate
            travelers = req.travelers
            budget = req.budget
            syncDays()
        }
        trips.saveAndFlush(trip)
        changed(trip, user)
        return view(trip, user)
    }

    @Transactional
    fun delete(user: User, id: String) {
        val trip = find(user, id)
        requireOwner(trip, user, "delete")
        val participants = access.participants(id)
        members.deleteAll(members.findByTripIdOrderByJoinedAtAsc(id))
        trips.delete(trip)
        access.evict(id)
        realtime.toUsers(participants, "trip.deleted", mapOf("tripId" to id))
    }

    @Transactional
    fun addActivity(user: User, tripId: String, dayNumber: Int, req: ActivityRequest): ActivityDto {
        val trip = find(user, tripId)
        val activity = req.toActivity()
        findDay(trip, dayNumber).addActivity(activity)
        trips.flush()
        changed(trip, user)
        return ActivityDto.from(activity)
    }

    @Transactional
    fun updateActivity(user: User, tripId: String, dayNumber: Int, activityId: String, req: ActivityRequest): ActivityDto {
        val trip = find(user, tripId)
        val a = findActivity(findDay(trip, dayNumber), activityId)
        a.update(req.title.trim(), req.time, req.type, req.note, req.costOrZero)
        changed(trip, user)
        return ActivityDto.from(a)
    }

    @Transactional
    fun deleteActivity(user: User, tripId: String, dayNumber: Int, activityId: String) {
        val trip = find(user, tripId)
        val day = findDay(trip, dayNumber)
        day.activities.remove(findActivity(day, activityId))
        changed(trip, user)
    }

    /** Replaces the order of a day's activities. The list must contain exactly the day's activity ids. */
    @Transactional
    fun reorder(user: User, tripId: String, dayNumber: Int, req: ReorderRequest): TripResponse {
        val trip = find(user, tripId)
        val day = findDay(trip, dayNumber)
        val ids = req.activityIds
        if (ids.size != day.activities.size || ids.toSet() != day.activities.map { it.id }.toSet()) {
            throw ApiException.badRequest("activityIds must contain each activity of day $dayNumber exactly once")
        }
        val reordered = ids.map { findActivity(day, it) }
        day.activities.clear()
        day.activities.addAll(reordered)
        trips.saveAndFlush(trip)
        changed(trip, user)
        return view(trip, user)
    }

    // ------------------------------------------------------------ group trips

    /** The trip's invite code, created on first use. Owner only. */
    @Transactional
    fun share(user: User, tripId: String): ShareCodeResponse {
        val trip = find(user, tripId)
        requireOwner(trip, user, "share")
        if (trip.shareCode == null) {
            var code: String
            do code = randomCode() while (trips.findByShareCode(code).isPresent)
            trip.shareCode = code
        }
        return ShareCodeResponse(trip.shareCode!!)
    }

    @Transactional
    fun join(user: User, req: JoinTripRequest): TripResponse {
        val code = req.code.trim().uppercase()
        val trip = trips.findByShareCode(code).orElseThrow { ApiException.notFound("Trip with code", code) }
        if (trip.user.id == user.id) throw ApiException.conflict("You already own this trip")
        if (members.existsByTripIdAndUserId(trip.tripId, user.uid)) throw ApiException.conflict("You have already joined this trip")

        val before = access.participants(trip.tripId)
        members.save(TripMember(trip, users.getReferenceById(user.uid)))
        access.evict(trip.tripId)

        notifications.notify(
            trip.user, NotificationType.TRIP, "${user.name} joined ${trip.title}",
            "They can now see and edit the itinerary and share their live location.",
        )
        realtime.toUsers(before, "trip.member_joined", mapOf("tripId" to trip.tripId, "userId" to user.uid, "name" to user.name))
        return view(trip, user)
    }

    @Transactional
    fun leave(user: User, tripId: String) {
        val m = members.findByTripIdAndUserId(tripId, user.uid)
            .orElseThrow { ApiException.notFound("Membership in trip", tripId) }
        members.delete(m)
        access.evict(tripId)
        realtime.toUsers(access.participants(tripId), "trip.updated", mapOf("tripId" to tripId))
    }

    @Transactional(readOnly = true)
    fun members(user: User, tripId: String): List<MemberDto> {
        val trip = find(user, tripId)
        val owner = trip.user
        return listOf(MemberDto(owner.uid, owner.name, "owner", hub.isOnline(owner.uid))) +
            members.findByTripIdOrderByJoinedAtAsc(tripId).map {
                MemberDto(it.user.uid, it.user.name, "member", hub.isOnline(it.user.uid))
            }
    }

    // ---------------------------------------------------------------- helpers

    /** Owner or companion; anyone else gets 404 so trip ids can't be probed. */
    fun find(user: User, id: String): Trip =
        trips.findAccessible(id, user.uid).orElseThrow { ApiException.notFound("Trip", id) }

    private fun view(trip: Trip, viewer: User) =
        TripResponse.from(trip, viewer.uid, members.countByTripId(trip.tripId).toInt())

    /** Tells every participant (on every device) to refresh this trip. */
    private fun changed(trip: Trip, by: User) =
        realtime.toUsers(access.participants(trip.tripId), "trip.updated", mapOf("tripId" to trip.tripId, "by" to by.name))

    private fun requireOwner(trip: Trip, user: User, action: String) {
        if (trip.user.id != user.id) throw ApiException.forbidden("Only the trip owner can $action this trip")
    }

    private fun randomCode() = (1..6).map { CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)] }.joinToString("")

    private fun findDay(trip: Trip, dayNumber: Int) = trip.day(dayNumber) ?: throw ApiException.notFound("Day", dayNumber)

    private fun findActivity(day: ItineraryDay, activityId: String) =
        day.activities.firstOrNull { it.id == activityId } ?: throw ApiException.notFound("Activity", activityId)

    private fun ActivityRequest.toActivity() = Activity(title.trim(), time, type, note.orEmpty(), costOrZero)

    private fun validateDates(start: LocalDate, end: LocalDate) {
        if (end.isBefore(start)) throw ApiException.badRequest("endDate must be on or after startDate")
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) throw ApiException.badRequest("Trips are limited to $MAX_DAYS days")
    }
}
