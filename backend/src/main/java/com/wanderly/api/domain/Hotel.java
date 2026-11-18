package com.wanderly.api.domain;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "hotels")
public class Hotel {

    @Id
    @Column(length = 60)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 250)
    private String address;

    @ElementCollection
    @CollectionTable(name = "hotel_images", joinColumns = @JoinColumn(name = "hotel_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500)
    private List<String> imageUrls = new ArrayList<>();

    @Column(name = "price_per_night", nullable = false)
    private int pricePerNight;

    private double rating;

    @Column(name = "review_count")
    private int reviewCount;

    @ElementCollection
    @CollectionTable(name = "hotel_amenities", joinColumns = @JoinColumn(name = "hotel_id"))
    @OrderColumn(name = "position")
    @Column(name = "amenity", length = 80)
    private List<String> amenities = new ArrayList<>();

    @Column(length = 1500)
    private String description;

    private int stars;

    protected Hotel() {
    }

    public Hotel(String id, Destination destination, String name, String address, int pricePerNight,
                 double rating, int reviewCount, int stars, String description) {
        this.id = id;
        this.destination = destination;
        this.name = name;
        this.address = address;
        this.pricePerNight = pricePerNight;
        this.rating = rating;
        this.reviewCount = reviewCount;
        this.stars = stars;
        this.description = description;
    }

    public String getId() { return id; }
    public Destination getDestination() { return destination; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public List<String> getImageUrls() { return imageUrls; }
    public int getPricePerNight() { return pricePerNight; }
    public double getRating() { return rating; }
    public int getReviewCount() { return reviewCount; }
    public List<String> getAmenities() { return amenities; }
    public String getDescription() { return description; }
    public int getStars() { return stars; }
}
