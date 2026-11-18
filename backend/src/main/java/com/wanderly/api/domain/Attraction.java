package com.wanderly.api.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "attractions")
public class Attraction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id")
    private Destination destination;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "attraction_type", length = 60)
    private String type;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    private double rating;

    @Column(name = "distance_km")
    private double distanceKm;

    /** Optional coordinates for the future map screen. */
    private Double latitude;
    private Double longitude;

    protected Attraction() {
    }

    public Attraction(String name, String type, String imageUrl, double rating, double distanceKm) {
        this.name = name;
        this.type = type;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.distanceKm = distanceKm;
    }

    void setDestination(Destination destination) { this.destination = destination; }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getType() { return type; }
    public String getImageUrl() { return imageUrl; }
    public double getRating() { return rating; }
    public double getDistanceKm() { return distanceKm; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
}
