package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "itinerary_days", uniqueConstraints = @UniqueConstraint(columnNames = {"trip_id", "day_number"}))
public class ItineraryDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @Column(name = "day_number", nullable = false)
    private int dayNumber;

    /** Order of this list is the order shown in the app (drag-to-reorder). */
    @OneToMany(mappedBy = "day", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderColumn(name = "position")
    private List<Activity> activities = new ArrayList<>();

    protected ItineraryDay() {
    }

    public ItineraryDay(Trip trip, int dayNumber) {
        this.trip = trip;
        this.dayNumber = dayNumber;
    }

    public void addActivity(Activity a) {
        a.setDay(this);
        activities.add(a);
    }

    public Long getId() { return id; }
    public Trip getTrip() { return trip; }
    public int getDayNumber() { return dayNumber; }
    public List<Activity> getActivities() { return activities; }
}
