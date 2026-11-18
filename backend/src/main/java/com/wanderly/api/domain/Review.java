package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    /** Null for seeded reviews. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(nullable = false, length = 100)
    private String author;

    private double rating;

    @Column(name = "review_comment", length = 1500)
    private String comment;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    protected Review() {
    }

    public Review(String author, double rating, String comment, LocalDate reviewDate) {
        this.author = author;
        this.rating = rating;
        this.comment = comment;
        this.reviewDate = reviewDate;
    }

    void setDestination(Destination destination) { this.destination = destination; }
    public void setUser(User user) { this.user = user; }

    public Long getId() { return id; }
    public String getAuthor() { return author; }
    public double getRating() { return rating; }
    public String getComment() { return comment; }
    public LocalDate getReviewDate() { return reviewDate; }
}
