package com.wanderly.api.service;

import com.wanderly.api.domain.Enums.ExpenseCategory;
import com.wanderly.api.domain.Enums.NotificationType;
import com.wanderly.api.domain.Expense;
import com.wanderly.api.domain.Trip;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.CommonDtos.*;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.ExpenseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;

@Service
public class BudgetService {

    private final ExpenseRepository expenses;
    private final TripService trips;
    private final UserService users;
    private final NotificationService notifications;

    public BudgetService(ExpenseRepository expenses, TripService trips, UserService users,
                         NotificationService notifications) {
        this.expenses = expenses;
        this.trips = trips;
        this.users = users;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<ExpenseDto> list(User user, String tripId) {
        var list = tripId == null
                ? expenses.findByUserIdOrderByDateDesc(user.getId())
                : expenses.findByUserIdAndTripIdOrderByDateDesc(user.getId(), tripId);
        return list.stream().map(ExpenseDto::from).toList();
    }

    @Transactional
    public ExpenseDto add(User user, ExpenseRequest req) {
        Trip trip = req.tripId() == null ? null : trips.find(user, req.tripId());
        var before = summary(user, req.tripId());
        var saved = expenses.save(new Expense(user, trip, req.title().trim(), req.amount(), req.category(),
                req.date() == null ? LocalDate.now() : req.date()));
        var after = summary(user, req.tripId());
        // Budget alerts fire once, when a threshold is crossed.
        if (after.isOver() && !before.isOver()) {
            notifications.notify(user, NotificationType.BUDGET, "Budget exceeded",
                    "You are ৳" + (-after.remaining()) + " over your budget.");
        } else if (after.isNearLimit() && !before.isNearLimit() && !before.isOver()) {
            notifications.notify(user, NotificationType.BUDGET, "80% of budget used",
                    "Only ৳" + after.remaining() + " left in your budget.");
        }
        return ExpenseDto.from(saved);
    }

    @Transactional
    public void delete(User user, String id) {
        expenses.delete(expenses.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> ApiException.notFound("Expense", id)));
    }

    @Transactional
    public BudgetSummary setTotal(User principal, int total) {
        User user = users.load(principal);
        user.setBudgetTotal(total);
        return summary(user, null);
    }

    /** Overall budget (user setting) or, with a tripId, that trip's budget and expenses. */
    @Transactional(readOnly = true)
    public BudgetSummary summary(User user, String tripId) {
        int total = tripId == null ? users.load(user).getBudgetTotal() : trips.find(user, tripId).getBudget();
        var byCategory = new EnumMap<ExpenseCategory, Integer>(ExpenseCategory.class);
        int spent = 0;
        for (var e : tripId == null
                ? expenses.findByUserIdOrderByDateDesc(user.getId())
                : expenses.findByUserIdAndTripIdOrderByDateDesc(user.getId(), tripId)) {
            byCategory.merge(e.getCategory(), e.getAmount(), Integer::sum);
            spent += e.getAmount();
        }
        double used = total == 0 ? 0 : (double) spent / total;
        boolean over = spent > total;
        boolean near = !over && used >= 0.8;
        String alert = over ? "You are ৳" + (spent - total) + " over budget"
                : near ? "You have used " + Math.round(used * 100) + "% of your budget" : null;
        return new BudgetSummary(total, spent, total - spent, Math.round(used * 1000) / 1000.0, over, near,
                byCategory, alert);
    }
}
