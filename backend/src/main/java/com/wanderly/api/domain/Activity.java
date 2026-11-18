package com.wanderly.api.domain;

import com.wanderly.api.domain.Enums.ActivityType;
import jakarta.persistence.*;

@Entity
@Table(name = "activities")
public class Activity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "day_id")
    private ItineraryDay day;

    @Column(nullable = false, length = 200)
    private String title;

    /** "HH:mm", e.g. "09:30". */
    @Column(name = "start_time", nullable = false, length = 5)
    private String time;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 20)
    private ActivityType type;

    @Column(length = 500)
    private String note = "";

    private int cost;

    protected Activity() {
    }

    public Activity(String title, String time, ActivityType type, String note, int cost) {
        this.title = title;
        this.time = time;
        this.type = type;
        this.note = note == null ? "" : note;
        this.cost = cost;
    }

    public void update(String title, String time, ActivityType type, String note, int cost) {
        this.title = title;
        this.time = time;
        this.type = type;
        this.note = note == null ? "" : note;
        this.cost = cost;
    }

    void setDay(ItineraryDay day) { this.day = day; }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getTime() { return time; }
    public ActivityType getType() { return type; }
    public String getNote() { return note; }
    public int getCost() { return cost; }
}
