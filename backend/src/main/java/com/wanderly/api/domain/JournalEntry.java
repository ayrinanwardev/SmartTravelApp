package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(length = 5000)
    private String body;

    @Column(length = 150)
    private String location;

    @Column(name = "entry_date", nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 100)
    private String album;

    /** URLs returned by POST /api/journal/photos (or any external URL). */
    @ElementCollection
    @CollectionTable(name = "journal_photos", joinColumns = @JoinColumn(name = "entry_id"))
    @OrderColumn(name = "position")
    @Column(name = "photo_url", length = 500)
    private List<String> photoUrls = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected JournalEntry() {
    }

    public JournalEntry(User user) {
        this.user = user;
    }

    public void update(String title, String body, String location, LocalDate date, String album, List<String> photoUrls) {
        this.title = title;
        this.body = body;
        this.location = location;
        this.date = date;
        this.album = album;
        this.photoUrls.clear();
        if (photoUrls != null) this.photoUrls.addAll(photoUrls);
    }

    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getLocation() { return location; }
    public LocalDate getDate() { return date; }
    public String getAlbum() { return album; }
    public List<String> getPhotoUrls() { return photoUrls; }
    public Instant getCreatedAt() { return createdAt; }
}
