package com.wanderly.api.service

import com.wanderly.api.config.WanderlyProperties
import com.wanderly.api.domain.*
import com.wanderly.api.dto.*
import com.wanderly.api.exception.ApiException
import com.wanderly.api.repository.ExpenseRepository
import com.wanderly.api.repository.JournalRepository
import com.wanderly.api.repository.WishlistRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.util.EnumMap
import java.util.UUID
import kotlin.math.roundToLong

@Service
class WishlistService(private val repo: WishlistRepository, private val destinations: DestinationService) {

    @Transactional(readOnly = true)
    fun list(user: User) = repo.findByUserIdOrderByCreatedAtDesc(user.uid).map { DestinationSummary.from(it.destination) }

    @Transactional(readOnly = true)
    fun ids(user: User) = repo.findByUserIdOrderByCreatedAtDesc(user.uid).map { it.destination.id }

    /** Idempotent: saving an already-saved destination is a no-op. */
    @Transactional
    fun add(user: User, destinationId: String) {
        if (repo.findByUserIdAndDestinationId(user.uid, destinationId).isEmpty) {
            repo.save(WishlistItem(user, destinations.find(destinationId)))
        }
    }

    @Transactional
    fun remove(user: User, destinationId: String) {
        repo.findByUserIdAndDestinationId(user.uid, destinationId).ifPresent(repo::delete)
    }
}

@Service
class BudgetService(
    private val expenses: ExpenseRepository,
    private val trips: TripService,
    private val users: UserService,
    private val notifications: NotificationService,
) {
    @Transactional(readOnly = true)
    fun list(user: User, tripId: String?): List<ExpenseDto> = byScope(user, tripId).map(ExpenseDto::from)

    @Transactional
    fun add(user: User, req: ExpenseRequest): ExpenseDto {
        val trip = req.tripId?.let { trips.find(user, it) }
        val before = summary(user, req.tripId)
        val saved = expenses.save(Expense(user, trip, req.title.trim(), req.amount, req.category, req.date ?: LocalDate.now()))
        val after = summary(user, req.tripId)
        // Budget alerts fire once, when a threshold is crossed.
        if (after.isOver && !before.isOver) {
            notifications.notify(user, NotificationType.BUDGET, "Budget exceeded", "You are ৳${-after.remaining} over your budget.")
        } else if (after.isNearLimit && !before.isNearLimit && !before.isOver) {
            notifications.notify(user, NotificationType.BUDGET, "80% of budget used", "Only ৳${after.remaining} left in your budget.")
        }
        return ExpenseDto.from(saved)
    }

    @Transactional
    fun delete(user: User, id: String) {
        expenses.delete(expenses.findByIdAndUserId(id, user.uid).orElseThrow { ApiException.notFound("Expense", id) })
    }

    @Transactional
    fun setTotal(principal: User, total: Int): BudgetSummary {
        val user = users.load(principal)
        user.budgetTotal = total
        return summary(user, null)
    }

    /** Overall budget (user setting) or, with a tripId, that trip's budget and expenses. */
    @Transactional(readOnly = true)
    fun summary(user: User, tripId: String?): BudgetSummary {
        val total = if (tripId == null) users.load(user).budgetTotal else trips.find(user, tripId).budget
        val byCategory = EnumMap<ExpenseCategory, Int>(ExpenseCategory::class.java)
        var spent = 0
        for (e in byScope(user, tripId)) {
            byCategory.merge(e.category, e.amount, Int::plus)
            spent += e.amount
        }
        val used = if (total == 0) 0.0 else spent.toDouble() / total
        val over = spent > total
        val near = !over && used >= 0.8
        val alert = when {
            over -> "You are ৳${spent - total} over budget"
            near -> "You have used ${(used * 100).roundToLong()}% of your budget"
            else -> null
        }
        return BudgetSummary(total, spent, total - spent, (used * 1000).roundToLong() / 1000.0, over, near, byCategory, alert)
    }

    private fun byScope(user: User, tripId: String?) =
        if (tripId == null) expenses.findByUserIdOrderByDateDesc(user.uid)
        else expenses.findByUserIdAndTripIdOrderByDateDesc(user.uid, tripId)
}

@Service
class JournalService(private val repo: JournalRepository) {

    @Transactional(readOnly = true)
    fun list(user: User, album: String?): List<JournalDto> =
        (if (album.isNullOrBlank()) repo.findByUserIdOrderByDateDesc(user.uid)
        else repo.findByUserIdAndAlbumOrderByDateDesc(user.uid, album)).map(JournalDto::from)

    @Transactional(readOnly = true)
    fun albums(user: User): List<String> = repo.findAlbums(user.uid)

    @Transactional(readOnly = true)
    fun get(user: User, id: String) = JournalDto.from(find(user, id))

    @Transactional
    fun create(user: User, req: JournalRequest) = JournalDto.from(repo.save(JournalEntry(user).also { it.fill(req) }))

    @Transactional
    fun update(user: User, id: String, req: JournalRequest) =
        JournalDto.from(repo.saveAndFlush(find(user, id).also { it.fill(req) }))

    @Transactional
    fun delete(user: User, id: String) = repo.delete(find(user, id))

    private fun JournalEntry.fill(r: JournalRequest) = update(
        r.title.trim(),
        r.body?.trim().orEmpty(),
        r.location?.takeIf { it.isNotBlank() }?.trim() ?: "Somewhere wonderful",
        r.date,
        r.album?.takeIf { it.isNotBlank() }?.trim() ?: "My Travels",
        r.photoUrls,
    )

    private fun find(user: User, id: String) =
        repo.findByIdAndUserId(id, user.uid).orElseThrow { ApiException.notFound("Journal entry", id) }
}

/**
 * Stores journal photos on local disk and serves them from /uploads/{file}.
 * Swap for Supabase Storage / S3 in production (Render's disk is wiped on deploy).
 */
@Service
class FileStorageService(props: WanderlyProperties) {

    companion object {
        private val ALLOWED = mapOf("image/jpeg" to ".jpg", "image/png" to ".png", "image/webp" to ".webp", "image/heic" to ".heic")
    }

    val root: Path = Path.of(props.uploads.dir).toAbsolutePath().normalize().also { Files.createDirectories(it) }

    /** Saves the image under a random name and returns its public path, e.g. "/uploads/3f2a….jpg". */
    fun storeImage(file: MultipartFile?): String {
        if (file == null || file.isEmpty) throw ApiException.badRequest("File is empty")
        val ext = ALLOWED[file.contentType] ?: throw ApiException.badRequest("Only JPEG, PNG, WEBP or HEIC images are allowed")
        val name = "${UUID.randomUUID()}$ext"
        try {
            file.inputStream.use { Files.copy(it, root.resolve(name), StandardCopyOption.REPLACE_EXISTING) }
        } catch (e: IOException) {
            throw IllegalStateException("Could not store file", e)
        }
        return "/uploads/$name"
    }
}
