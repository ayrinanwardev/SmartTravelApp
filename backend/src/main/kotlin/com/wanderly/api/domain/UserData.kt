package com.wanderly.api.domain

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "bookings")
class Booking(
    /** Confirmation code, e.g. "WND-48213977". */
    @Id @Column(length = 30) var id: String,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "hotel_id") var hotel: Hotel,
    @Column(name = "check_in", nullable = false) var checkIn: LocalDate,
    @Column(name = "check_out", nullable = false) var checkOut: LocalDate,
    var guests: Int,
    var rooms: Int,
    var subtotal: Int,
    var taxes: Int,
) {
    var total: Int = subtotal + taxes

    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    var status: BookingStatus = BookingStatus.CONFIRMED

    // Payment columns are nullable so they could be added to an existing table without a migration.
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", length = 20)
    private var paymentStatusRaw: PaymentStatus? = PaymentStatus.UNPAID

    /** Gateway transaction id of the latest payment attempt. */
    @Column(name = "payment_tran_id", unique = true, length = 60) var paymentTranId: String? = null
    @Column(name = "payment_val_id", length = 80) var paymentValId: String? = null
    /** e.g. "BKASH-BKash", "VISA-Dutch Bangla". */
    @Column(name = "payment_method", length = 60) var paymentMethod: String? = null
    @Column(name = "paid_at") var paidAt: Instant? = null

    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()

    /** Bookings made before online payments existed count as paid. */
    val paymentStatus: PaymentStatus get() = paymentStatusRaw ?: PaymentStatus.PAID

    fun startPayment(tranId: String) {
        paymentTranId = tranId
        paymentStatusRaw = PaymentStatus.UNPAID
    }

    fun markPaid(valId: String, method: String?) {
        paymentStatusRaw = PaymentStatus.PAID
        paymentValId = valId
        paymentMethod = method
        paidAt = Instant.now()
    }

    fun markPaymentFailed() {
        paymentStatusRaw = PaymentStatus.FAILED
    }
}

@Entity
@Table(name = "wishlist_items", uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "destination_id"])])
class WishlistItem(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "destination_id") var destination: Destination,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "expenses")
class Expense(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
    /** Optional: an expense can belong to a specific trip. */
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "trip_id") var trip: Trip?,
    @Column(nullable = false, length = 150) var title: String,
    @Column(nullable = false) var amount: Int,
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) var category: ExpenseCategory,
    @Column(name = "expense_date", nullable = false) var date: LocalDate,
) {
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: String? = null
}

@Entity
@Table(name = "journal_entries")
class JournalEntry(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
) {
    @Id @GeneratedValue(strategy = GenerationType.UUID) var id: String? = null
    @Column(nullable = false, length = 150) var title: String = ""
    @Column(length = 5000) var body: String? = null
    @Column(length = 150) var location: String? = null
    @Column(name = "entry_date", nullable = false) var date: LocalDate = LocalDate.now()
    @Column(nullable = false, length = 100) var album: String = "My Travels"

    /** URLs returned by POST /api/journal/photos (or any external URL). */
    @ElementCollection
    @CollectionTable(name = "journal_photos", joinColumns = [JoinColumn(name = "entry_id")])
    @OrderColumn(name = "position")
    @Column(name = "photo_url", length = 500)
    var photoUrls: MutableList<String> = mutableListOf()

    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()

    fun update(title: String, body: String, location: String, date: LocalDate, album: String, photoUrls: List<String>?) {
        this.title = title
        this.body = body
        this.location = location
        this.date = date
        this.album = album
        this.photoUrls.clear()
        photoUrls?.let { this.photoUrls.addAll(it) }
    }
}

@Entity
@Table(name = "notifications")
class Notification(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
    @Enumerated(EnumType.STRING) @Column(name = "notification_type", nullable = false, length = 20) var type: NotificationType,
    @Column(nullable = false, length = 150) var title: String,
    @Column(length = 500) var message: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @Column(name = "is_read", nullable = false) var read: Boolean = false
    @Column(name = "created_at", nullable = false) var createdAt: Instant = Instant.now()
}

/** An FCM registration token for one of the user's devices. */
@Entity
@Table(name = "device_tokens")
class DeviceToken(
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id") var user: User,
    @Column(nullable = false, unique = true, length = 512) var token: String,
    @Column(length = 20) var platform: String?,
) {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) var id: Long? = null
    @Column(name = "updated_at", nullable = false) var updatedAt: Instant = Instant.now()

    /** A token moves to whoever signed in on the device most recently. */
    fun assignTo(user: User, platform: String?) {
        this.user = user
        this.platform = platform
        updatedAt = Instant.now()
    }
}
