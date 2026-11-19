package com.wanderly.api.repository

import com.wanderly.api.domain.*
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.util.Optional

interface UserRepository : JpaRepository<User, Long> {
    fun findByEmailIgnoreCase(email: String): Optional<User>
    fun existsByEmailIgnoreCase(email: String): Boolean
}

interface DestinationRepository : JpaRepository<Destination, String>

interface HotelRepository : JpaRepository<Hotel, String> {
    fun findByDestinationIdOrderByRatingDesc(destinationId: String): List<Hotel>
    fun findAllByOrderByRatingDesc(): List<Hotel>
}

interface BookingRepository : JpaRepository<Booking, String> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Booking>
    fun findByIdAndUserId(id: String, userId: Long): Optional<Booking>
    fun findByPaymentTranId(paymentTranId: String): Optional<Booking>
    fun countByUserId(userId: Long): Long
}

interface TripRepository : JpaRepository<Trip, String> {
    fun findByShareCode(shareCode: String): Optional<Trip>
    fun countByUserId(userId: Long): Long

    /** Trips the user owns or has joined as a companion. */
    @Query(
        """select distinct t from Trip t
           where t.user.id = :userId
              or exists (select 1 from TripMember m where m.trip = t and m.user.id = :userId)
           order by t.startDate asc"""
    )
    fun findAccessible(userId: Long): List<Trip>

    @Query(
        """select t from Trip t
           where t.id = :id and (t.user.id = :userId
              or exists (select 1 from TripMember m where m.trip = t and m.user.id = :userId))"""
    )
    fun findAccessible(id: String, userId: Long): Optional<Trip>
}

interface TripMemberRepository : JpaRepository<TripMember, Long> {
    fun findByTripIdOrderByJoinedAtAsc(tripId: String): List<TripMember>
    fun findByTripIdAndUserId(tripId: String, userId: Long): Optional<TripMember>
    fun existsByTripIdAndUserId(tripId: String, userId: Long): Boolean
    fun countByTripId(tripId: String): Long

    @Query("select m.user.id from TripMember m where m.trip.id = :tripId")
    fun findUserIds(tripId: String): List<Long>
}

interface WishlistRepository : JpaRepository<WishlistItem, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<WishlistItem>
    fun findByUserIdAndDestinationId(userId: Long, destinationId: String): Optional<WishlistItem>
    fun countByUserId(userId: Long): Long
}

interface ExpenseRepository : JpaRepository<Expense, String> {
    fun findByUserIdOrderByDateDesc(userId: Long): List<Expense>
    fun findByUserIdAndTripIdOrderByDateDesc(userId: Long, tripId: String): List<Expense>
    fun findByIdAndUserId(id: String, userId: Long): Optional<Expense>
}

interface JournalRepository : JpaRepository<JournalEntry, String> {
    fun findByUserIdOrderByDateDesc(userId: Long): List<JournalEntry>
    fun findByUserIdAndAlbumOrderByDateDesc(userId: Long, album: String): List<JournalEntry>
    fun findByIdAndUserId(id: String, userId: Long): Optional<JournalEntry>
    fun countByUserId(userId: Long): Long

    @Query("select distinct j.album from JournalEntry j where j.user.id = :userId order by j.album")
    fun findAlbums(userId: Long): List<String>
}

interface NotificationRepository : JpaRepository<Notification, Long> {
    fun findByUserIdOrderByCreatedAtDesc(userId: Long): List<Notification>
    fun findByIdAndUserId(id: Long, userId: Long): Optional<Notification>
    fun countByUserIdAndReadFalse(userId: Long): Long
}

interface DeviceTokenRepository : JpaRepository<DeviceToken, Long> {
    fun findByToken(token: String): Optional<DeviceToken>

    @Query("select d.token from DeviceToken d where d.user.id = :userId")
    fun findTokens(userId: Long): List<String>

    @Modifying
    @Query("delete from DeviceToken d where d.token in :tokens")
    fun deleteByTokenIn(tokens: Collection<String>): Int

    @Modifying
    @Query("delete from DeviceToken d where d.token = :token and d.user.id = :userId")
    fun deleteByTokenAndUserId(token: String, userId: Long): Int
}
