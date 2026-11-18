package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "trips")
public class Trip {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    private int travelers;
    private int budget;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    private List<ItineraryDay> days = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Trip() {
    }

    public Trip(User user, Destination destination, String title, LocalDate startDate, LocalDate endDate,
                int travelers, int budget) {
        this.user = user;
        this.destination = destination;
        this.title = title;
        this.imageUrl = destination.getImageUrl();
        this.startDate = startDate;
        this.endDate = endDate;
        this.travelers = travelers;
        this.budget = budget;
    }

    public int dayCount() {
        return (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
    }

    /** Grows or shrinks the itinerary so there is exactly one day per date (keeps existing days). */
    public void syncDays() {
        int count = dayCount();
        days.removeIf(d -> d.getDayNumber() > count);
        for (int n = days.size() + 1; n <= count; n++) {
            days.add(new ItineraryDay(this, n));
        }
    }

    public ItineraryDay day(int dayNumber) {
        return days.stream().filter(d -> d.getDayNumber() == dayNumber).findFirst().orElse(null);
    }

    public String getId() { return id; }
    public User getUser() { return user; }
    public Destination getDestination() { return destination; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getImageUrl() { return imageUrl; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public int getTravelers() { return travelers; }
    public void setTravelers(int travelers) { this.travelers = travelers; }
    public int getBudget() { return budget; }
    public void setBudget(int budget) { this.budget = budget; }
    public List<ItineraryDay> getDays() { return days; }
    public Instant getCreatedAt() { return createdAt; }
}
