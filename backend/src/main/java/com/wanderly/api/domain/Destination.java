package com.wanderly.api.domain;

import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.Season;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "destinations")
public class Destination {

    /** Human-readable slug, e.g. "coxs-bazar" — same ids the Flutter app uses. */
    @Id
    @Column(length = 60)
    private String id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 80)
    private String country;

    @Column(length = 200)
    private String tagline;

    @Column(length = 3000)
    private String description;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @ElementCollection
    @CollectionTable(name = "destination_gallery", joinColumns = @JoinColumn(name = "destination_id"))
    @OrderColumn(name = "position")
    @Column(name = "image_url", length = 500)
    private List<String> gallery = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DestinationCategory category;

    @ElementCollection
    @CollectionTable(name = "destination_seasons", joinColumns = @JoinColumn(name = "destination_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "season", length = 20)
    private Set<Season> bestSeasons = new LinkedHashSet<>();

    @Column(name = "best_time", length = 80)
    private String bestTime;

    private double rating;

    @Column(name = "review_count")
    private int reviewCount;

    @Column(name = "avg_temp_c")
    private int avgTempC;

    @Embedded
    private CostEstimate cost = new CostEstimate();

    @ElementCollection
    @CollectionTable(name = "destination_highlights", joinColumns = @JoinColumn(name = "destination_id"))
    @OrderColumn(name = "position")
    @Column(name = "highlight", length = 120)
    private List<String> highlights = new ArrayList<>();

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("rating DESC")
    private List<Attraction> attractions = new ArrayList<>();

    @OneToMany(mappedBy = "destination", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("reviewDate DESC")
    private List<Review> reviews = new ArrayList<>();

    private boolean trending;
    private boolean popular;

    protected Destination() {
    }

    public Destination(String id, String name, String country, DestinationCategory category) {
        this.id = id;
        this.name = name;
        this.country = country;
        this.category = category;
    }

    /** Per-person estimate for a typical 3-day trip (used by budget filters). */
    public int typicalTripCost() {
        return cost.forDays(3, 1);
    }

    public void addAttraction(Attraction a) {
        a.setDestination(this);
        attractions.add(a);
    }

    public void addReview(Review r) {
        r.setDestination(this);
        reviews.add(r);
        double total = rating * reviewCount + r.getRating();
        reviewCount++;
        rating = Math.round(total / reviewCount * 10) / 10.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCountry() { return country; }
    public String getTagline() { return tagline; }
    public void setTagline(String tagline) { this.tagline = tagline; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public List<String> getGallery() { return gallery; }
    public DestinationCategory getCategory() { return category; }
    public Set<Season> getBestSeasons() { return bestSeasons; }
    public String getBestTime() { return bestTime; }
    public void setBestTime(String bestTime) { this.bestTime = bestTime; }
    public double getRating() { return rating; }
    public void setRating(double rating) { this.rating = rating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public int getAvgTempC() { return avgTempC; }
    public void setAvgTempC(int avgTempC) { this.avgTempC = avgTempC; }
    public CostEstimate getCost() { return cost; }
    public void setCost(CostEstimate cost) { this.cost = cost; }
    public List<String> getHighlights() { return highlights; }
    public List<Attraction> getAttractions() { return attractions; }
    public List<Review> getReviews() { return reviews; }
    public boolean isTrending() { return trending; }
    public void setTrending(boolean trending) { this.trending = trending; }
    public boolean isPopular() { return popular; }
    public void setPopular(boolean popular) { this.popular = popular; }
}
