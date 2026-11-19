package com.wanderly.api.controller

import com.wanderly.api.domain.DestinationCategory
import com.wanderly.api.domain.Season
import com.wanderly.api.domain.User
import com.wanderly.api.dto.*
import com.wanderly.api.service.*
import com.wanderly.api.service.DestinationService.Filter
import com.wanderly.api.service.DestinationService.Sort
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/auth")
class AuthController(private val auth: AuthService) {

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    fun register(@Valid @RequestBody req: RegisterRequest) = auth.register(req)

    @PostMapping("/login")
    fun login(@Valid @RequestBody req: LoginRequest) = auth.login(req)
}

@RestController
@RequestMapping("/api/destinations")
class DestinationController(private val service: DestinationService) {

    /**
     * `GET /api/destinations?q=bali&category=beach&season=winter&maxBudget=30000&sort=price_low`.
     * With `full=true` each item is a full DestinationDetail (the app loads the catalogue once
     * this way); otherwise lightweight DestinationSummary cards.
     */
    @GetMapping
    fun search(
        @RequestParam(required = false) q: String?,
        @RequestParam(required = false) category: String?,
        @RequestParam(required = false) season: String?,
        @RequestParam(required = false) maxBudget: Int?,
        @RequestParam(required = false) trending: Boolean?,
        @RequestParam(required = false) popular: Boolean?,
        @RequestParam(defaultValue = "rating") sort: String,
        @RequestParam(defaultValue = "false") full: Boolean,
    ): List<Any> {
        val filter = Filter(
            q, category?.let { DestinationCategory.valueOf(it.uppercase()) }, season?.let { Season.valueOf(it.uppercase()) },
            maxBudget, trending, popular, Sort.valueOf(sort.uppercase()),
        )
        return if (full) service.searchFull(filter) else service.search(filter)
    }

    @GetMapping("/{id}")
    fun get(@PathVariable id: String) = service.get(id)

    @GetMapping("/{id}/weather")
    fun weather(@PathVariable id: String) = service.weather(id)

    @PostMapping("/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    fun addReview(@PathVariable id: String, @AuthenticationPrincipal user: User, @Valid @RequestBody req: ReviewRequest) =
        service.addReview(id, user, req)
}

@RestController
@RequestMapping("/api")
class HotelController(private val service: HotelService) {

    /** `GET /api/hotels?destinationId=bali&sort=price` */
    @GetMapping("/hotels")
    fun list(@RequestParam(required = false) destinationId: String?, @RequestParam(defaultValue = "rating") sort: String) =
        service.list(destinationId, sort.equals("price", ignoreCase = true))

    @GetMapping("/hotels/{id}")
    fun get(@PathVariable id: String) = service.get(id)

    @GetMapping("/bookings")
    fun myBookings(@AuthenticationPrincipal user: User) = service.myBookings(user)

    @GetMapping("/bookings/{id}")
    fun booking(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.getBooking(user, id)

    /** Reserves the stay (payment status "unpaid"); pay via /api/payments/bookings/{id}/session. */
    @PostMapping("/bookings")
    @ResponseStatus(HttpStatus.CREATED)
    fun book(@AuthenticationPrincipal user: User, @Valid @RequestBody req: BookingRequest) = service.book(user, req)

    @PatchMapping("/bookings/{id}/cancel")
    fun cancel(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.cancel(user, id)
}

@RestController
@RequestMapping("/api/trips")
class TripController(private val service: TripService) {

    /** `GET /api/trips?status=upcoming|past` — owned and joined trips. */
    @GetMapping
    fun list(@AuthenticationPrincipal user: User, @RequestParam(required = false) status: String?) = service.list(user, status)

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.get(user, id)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@AuthenticationPrincipal user: User, @Valid @RequestBody req: CreateTripRequest) = service.create(user, req)

    @PutMapping("/{id}")
    fun update(@AuthenticationPrincipal user: User, @PathVariable id: String, @Valid @RequestBody req: UpdateTripRequest) =
        service.update(user, id, req)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.delete(user, id)

    @PostMapping("/{id}/days/{day}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    fun addActivity(@AuthenticationPrincipal user: User, @PathVariable id: String, @PathVariable day: Int, @Valid @RequestBody req: ActivityRequest) =
        service.addActivity(user, id, day, req)

    @PutMapping("/{id}/days/{day}/activities/{activityId}")
    fun updateActivity(
        @AuthenticationPrincipal user: User, @PathVariable id: String, @PathVariable day: Int,
        @PathVariable activityId: String, @Valid @RequestBody req: ActivityRequest,
    ) = service.updateActivity(user, id, day, activityId, req)

    @DeleteMapping("/{id}/days/{day}/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteActivity(@AuthenticationPrincipal user: User, @PathVariable id: String, @PathVariable day: Int, @PathVariable activityId: String) =
        service.deleteActivity(user, id, day, activityId)

    /** Body: `{"activityIds": ["id3", "id1", "id2"]}` */
    @PutMapping("/{id}/days/{day}/activities/order")
    fun reorder(@AuthenticationPrincipal user: User, @PathVariable id: String, @PathVariable day: Int, @Valid @RequestBody req: ReorderRequest) =
        service.reorder(user, id, day, req)

    // ---------- Group trips ----------

    /** Owner gets (or creates) the invite code companions use to join. */
    @PostMapping("/{id}/share")
    fun share(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.share(user, id)

    /** Body: `{"code": "K7QX2M"}` */
    @PostMapping("/join")
    fun join(@AuthenticationPrincipal user: User, @Valid @RequestBody req: JoinTripRequest) = service.join(user, req)

    /** Owner + companions, with who is online right now. */
    @GetMapping("/{id}/members")
    fun members(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.members(user, id)

    @DeleteMapping("/{id}/members/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun leave(@AuthenticationPrincipal user: User, @PathVariable id: String) = service.leave(user, id)
}

/** The signed-in user's own data: profile, wishlist, budget, journal, notifications, devices. */
@RestController
@RequestMapping("/api")
class UserDataController(
    private val users: UserService,
    private val wishlist: WishlistService,
    private val budget: BudgetService,
    private val journal: JournalService,
    private val notifications: NotificationService,
    private val files: FileStorageService,
    private val push: PushService,
) {
    // ---------- Profile ----------
    @GetMapping("/users/me")
    fun me(@AuthenticationPrincipal user: User) = users.me(user)

    @PatchMapping("/users/me")
    fun updateMe(@AuthenticationPrincipal user: User, @Valid @RequestBody req: UpdateProfileRequest) = users.update(user, req)

    @GetMapping("/users/me/stats")
    fun stats(@AuthenticationPrincipal user: User) = users.stats(user)

    // ---------- Wishlist ----------
    @GetMapping("/wishlist")
    fun wishlist(@AuthenticationPrincipal user: User) = wishlist.list(user)

    @GetMapping("/wishlist/ids")
    fun wishlistIds(@AuthenticationPrincipal user: User) = wishlist.ids(user)

    @PutMapping("/wishlist/{destinationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun save(@AuthenticationPrincipal user: User, @PathVariable destinationId: String) = wishlist.add(user, destinationId)

    @DeleteMapping("/wishlist/{destinationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unsave(@AuthenticationPrincipal user: User, @PathVariable destinationId: String) = wishlist.remove(user, destinationId)

    // ---------- Budget & expenses ----------
    @GetMapping("/budget")
    fun budgetSummary(@AuthenticationPrincipal user: User, @RequestParam(required = false) tripId: String?) = budget.summary(user, tripId)

    @PutMapping("/budget")
    fun setBudget(@AuthenticationPrincipal user: User, @Valid @RequestBody req: BudgetUpdateRequest) = budget.setTotal(user, req.total)

    @GetMapping("/expenses")
    fun expenses(@AuthenticationPrincipal user: User, @RequestParam(required = false) tripId: String?) = budget.list(user, tripId)

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    fun addExpense(@AuthenticationPrincipal user: User, @Valid @RequestBody req: ExpenseRequest) = budget.add(user, req)

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteExpense(@AuthenticationPrincipal user: User, @PathVariable id: String) = budget.delete(user, id)

    // ---------- Journal ----------
    @GetMapping("/journal")
    fun journal(@AuthenticationPrincipal user: User, @RequestParam(required = false) album: String?) = journal.list(user, album)

    @GetMapping("/journal/albums")
    fun albums(@AuthenticationPrincipal user: User) = journal.albums(user)

    @GetMapping("/journal/{id}")
    fun journalEntry(@AuthenticationPrincipal user: User, @PathVariable id: String) = journal.get(user, id)

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    fun createEntry(@AuthenticationPrincipal user: User, @Valid @RequestBody req: JournalRequest) = journal.create(user, req)

    @PutMapping("/journal/{id}")
    fun updateEntry(@AuthenticationPrincipal user: User, @PathVariable id: String, @Valid @RequestBody req: JournalRequest) =
        journal.update(user, id, req)

    @DeleteMapping("/journal/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEntry(@AuthenticationPrincipal user: User, @PathVariable id: String) = journal.delete(user, id)

    /** Multipart upload (field "file"). Returns a URL to put in a journal entry's photoUrls. */
    @PostMapping("/journal/photos", consumes = ["multipart/form-data"])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadPhoto(@RequestParam("file") file: MultipartFile) = UploadResponse(files.storeImage(file))

    // ---------- Push devices (FCM) ----------
    /** Body: `{"token": "<FCM registration token>", "platform": "android"}` */
    @PostMapping("/devices")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun registerDevice(@AuthenticationPrincipal user: User, @Valid @RequestBody req: DeviceRequest) = push.register(user, req.token, req.platform)

    @PostMapping("/devices/unregister")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregisterDevice(@AuthenticationPrincipal user: User, @Valid @RequestBody req: DeviceRequest) = push.unregister(user, req.token)

    // ---------- Notifications ----------
    @GetMapping("/notifications")
    fun notifications(@AuthenticationPrincipal user: User) = notifications.list(user)

    @PatchMapping("/notifications/{id}/read")
    fun markRead(@AuthenticationPrincipal user: User, @PathVariable id: Long) = notifications.markRead(user, id)

    @PatchMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAllRead(@AuthenticationPrincipal user: User) = notifications.markAllRead(user)
}

/** Public helpers: trip planner, currency converter, packing list. */
@RestController
@RequestMapping("/api")
class ToolsController(private val ai: AiPlannerService, private val tools: ToolsService) {

    /** Body: `{"prompt": "Plan a 4-day trip to Cox's Bazar under ৳20,000"}` */
    @PostMapping("/ai/plan")
    fun plan(@RequestBody req: AiPlannerService.PlanRequest): AiPlannerService.PlanResponse {
        val prompt = req.prompt
        require(!prompt.isNullOrBlank()) { "prompt is required" }
        require(prompt.length <= 1000) { "prompt must be at most 1000 characters" }
        return ai.plan(prompt)
    }

    /** 1 unit of each currency in BDT. */
    @GetMapping("/currency/rates")
    fun rates() = ToolsService.BDT_RATES

    /** `GET /api/currency/convert?from=BDT&to=USD&amount=10000` */
    @GetMapping("/currency/convert")
    fun convert(@RequestParam from: String, @RequestParam to: String, @RequestParam amount: Double) = tools.convert(from, to, amount)

    /** `GET /api/packing?destinationId=sajek&days=4` */
    @GetMapping("/packing")
    fun packing(@RequestParam destinationId: String, @RequestParam(defaultValue = "4") days: Int) = tools.packing(destinationId, days)
}
