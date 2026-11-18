package com.wanderly.api.domain;

import com.wanderly.api.domain.Enums.ExpenseCategory;
import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "expenses")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    /** Optional: expenses can belong to a specific trip. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false)
    private int amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Column(name = "expense_date", nullable = false)
    private LocalDate date;

    protected Expense() {
    }

    public Expense(User user, Trip trip, String title, int amount, ExpenseCategory category, LocalDate date) {
        this.user = user;
        this.trip = trip;
        this.title = title;
        this.amount = amount;
        this.category = category;
        this.date = date;
    }

    public String getId() { return id; }
    public Trip getTrip() { return trip; }
    public String getTitle() { return title; }
    public int getAmount() { return amount; }
    public ExpenseCategory getCategory() { return category; }
    public LocalDate getDate() { return date; }
}
