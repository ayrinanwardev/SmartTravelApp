package com.wanderly.api.controller;

import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.*;
import com.wanderly.api.dto.DestinationDtos.DestinationSummary;
import com.wanderly.api.service.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Endpoints for the signed-in user's own data: profile, wishlist, budget, journal, notifications. */
@RestController
@RequestMapping("/api")
public class UserDataController {

    private final UserService users;
    private final WishlistService wishlist;
    private final BudgetService budget;
    private final JournalService journal;
    private final NotificationService notifications;
    private final FileStorageService files;

    public UserDataController(UserService users, WishlistService wishlist, BudgetService budget, JournalService journal,
                              NotificationService notifications, FileStorageService files) {
        this.users = users;
        this.wishlist = wishlist;
        this.budget = budget;
        this.journal = journal;
        this.notifications = notifications;
        this.files = files;
    }

    // ---------- Profile ----------

    @GetMapping("/users/me")
    public UserDto me(@AuthenticationPrincipal User user) {
        return users.me(user);
    }

    @PatchMapping("/users/me")
    public UserDto updateMe(@AuthenticationPrincipal User user, @Valid @RequestBody UpdateProfileRequest req) {
        return users.update(user, req);
    }

    @GetMapping("/users/me/stats")
    public ProfileStats stats(@AuthenticationPrincipal User user) {
        return users.stats(user);
    }

    // ---------- Wishlist ----------

    @GetMapping("/wishlist")
    public List<DestinationSummary> wishlist(@AuthenticationPrincipal User user) {
        return wishlist.list(user);
    }

    @GetMapping("/wishlist/ids")
    public List<String> wishlistIds(@AuthenticationPrincipal User user) {
        return wishlist.ids(user);
    }

    @PutMapping("/wishlist/{destinationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal User user, @PathVariable String destinationId) {
        wishlist.add(user, destinationId);
    }

    @DeleteMapping("/wishlist/{destinationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unsave(@AuthenticationPrincipal User user, @PathVariable String destinationId) {
        wishlist.remove(user, destinationId);
    }

    // ---------- Budget & expenses ----------

    @GetMapping("/budget")
    public BudgetSummary budgetSummary(@AuthenticationPrincipal User user, @RequestParam(required = false) String tripId) {
        return budget.summary(user, tripId);
    }

    @PutMapping("/budget")
    public BudgetSummary setBudget(@AuthenticationPrincipal User user, @Valid @RequestBody BudgetUpdateRequest req) {
        return budget.setTotal(user, req.total());
    }

    @GetMapping("/expenses")
    public List<ExpenseDto> expenses(@AuthenticationPrincipal User user, @RequestParam(required = false) String tripId) {
        return budget.list(user, tripId);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseDto addExpense(@AuthenticationPrincipal User user, @Valid @RequestBody ExpenseRequest req) {
        return budget.add(user, req);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@AuthenticationPrincipal User user, @PathVariable String id) {
        budget.delete(user, id);
    }

    // ---------- Journal ----------

    @GetMapping("/journal")
    public List<JournalDto> journal(@AuthenticationPrincipal User user, @RequestParam(required = false) String album) {
        return journal.list(user, album);
    }

    @GetMapping("/journal/albums")
    public List<String> albums(@AuthenticationPrincipal User user) {
        return journal.albums(user);
    }

    @GetMapping("/journal/{id}")
    public JournalDto journalEntry(@AuthenticationPrincipal User user, @PathVariable String id) {
        return journal.get(user, id);
    }

    @PostMapping("/journal")
    @ResponseStatus(HttpStatus.CREATED)
    public JournalDto createEntry(@AuthenticationPrincipal User user, @Valid @RequestBody JournalRequest req) {
        return journal.create(user, req);
    }

    @PutMapping("/journal/{id}")
    public JournalDto updateEntry(@AuthenticationPrincipal User user, @PathVariable String id,
                                  @Valid @RequestBody JournalRequest req) {
        return journal.update(user, id, req);
    }

    @DeleteMapping("/journal/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteEntry(@AuthenticationPrincipal User user, @PathVariable String id) {
        journal.delete(user, id);
    }

    /** Multipart upload (field name "file"). Returns a URL to put in a journal entry's photoUrls. */
    @PostMapping(value = "/journal/photos", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse uploadPhoto(@RequestParam("file") MultipartFile file) {
        return new UploadResponse(files.storeImage(file));
    }

    // ---------- Notifications ----------

    @GetMapping("/notifications")
    public List<NotificationDto> notifications(@AuthenticationPrincipal User user) {
        return notifications.list(user);
    }

    @PatchMapping("/notifications/{id}/read")
    public NotificationDto markRead(@AuthenticationPrincipal User user, @PathVariable Long id) {
        return notifications.markRead(user, id);
    }

    @PatchMapping("/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal User user) {
        notifications.markAllRead(user);
    }
}
