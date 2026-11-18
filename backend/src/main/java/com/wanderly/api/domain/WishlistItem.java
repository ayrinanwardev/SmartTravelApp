package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "wishlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "destination_id"}))
public class WishlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WishlistItem() {
    }

    public WishlistItem(User user, Destination destination) {
        this.user = user;
        this.destination = destination;
    }

    public Long getId() { return id; }
    public Destination getDestination() { return destination; }
    public Instant getCreatedAt() { return createdAt; }
}
