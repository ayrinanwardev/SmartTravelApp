package com.wanderly.api.dto;

import com.wanderly.api.domain.*;
import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.Season;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public final class DestinationDtos {

    private DestinationDtos() {
    }

    public record CostDto(int transport, int hotelPerNight, int foodPerDay, int activitiesPerDay) {
        static CostDto from(CostEstimate c) {
            return new CostDto(c.getTransport(), c.getHotelPerNight(), c.getFoodPerDay(), c.getActivitiesPerDay());
        }
    }

    public record AttractionDto(Long id, String name, String type, String imageUrl, double rating, double distanceKm,
                                Double latitude, Double longitude) {
        static AttractionDto from(Attraction a) {
            return new AttractionDto(a.getId(), a.getName(), a.getType(), a.getImageUrl(), a.getRating(),
                    a.getDistanceKm(), a.getLatitude(), a.getLongitude());
        }
    }

    public record ReviewDto(Long id, String author, double rating, String comment, LocalDate date) {
        public static ReviewDto from(Review r) {
            return new ReviewDto(r.getId(), r.getAuthor(), r.getRating(), r.getComment(), r.getReviewDate());
        }
    }

    /** Lightweight card used by lists (home, explore, wishlist). */
    public record DestinationSummary(String id, String name, String country, String tagline, String imageUrl,
                                     DestinationCategory category, List<Season> bestSeasons, double rating,
                                     int reviewCount, int hotelFromPerNight, int typicalTripCost,
                                     boolean isTrending, boolean isPopular) {
        public static DestinationSummary from(Destination d) {
            return new DestinationSummary(d.getId(), d.getName(), d.getCountry(), d.getTagline(), d.getImageUrl(),
                    d.getCategory(), List.copyOf(d.getBestSeasons()), d.getRating(), d.getReviewCount(),
                    d.getCost().getHotelPerNight(), d.typicalTripCost(), d.isTrending(), d.isPopular());
        }
    }

    /** Full detail payload for the Destination Details screen. */
    public record DestinationDetail(String id, String name, String country, String tagline, String description,
                                    String imageUrl, List<String> gallery, DestinationCategory category,
                                    List<Season> bestSeasons, String bestTime, double rating, int reviewCount,
                                    int avgTempC, CostDto cost, int typicalTripCost, List<String> highlights,
                                    List<AttractionDto> attractions, List<ReviewDto> reviews,
                                    boolean isTrending, boolean isPopular) {
        public static DestinationDetail from(Destination d) {
            return new DestinationDetail(d.getId(), d.getName(), d.getCountry(), d.getTagline(), d.getDescription(),
                    d.getImageUrl(), List.copyOf(d.getGallery()), d.getCategory(), List.copyOf(d.getBestSeasons()),
                    d.getBestTime(), d.getRating(), d.getReviewCount(), d.getAvgTempC(), CostDto.from(d.getCost()),
                    d.typicalTripCost(), List.copyOf(d.getHighlights()),
                    d.getAttractions().stream().map(AttractionDto::from).toList(),
                    d.getReviews().stream().map(ReviewDto::from).toList(),
                    d.isTrending(), d.isPopular());
        }
    }

    public record ReviewRequest(
            @DecimalMin("1.0") @DecimalMax("5.0") double rating,
            @NotBlank @Size(max = 1500) String comment) {
    }

    public record WeatherDay(LocalDate date, int tempC, String condition) {
    }

    public record WeatherForecast(String destinationId, List<WeatherDay> days, String suggestion) {
    }

    public record PackingList(String destinationId, int days, List<PackingSection> sections) {
    }

    public record PackingSection(String title, List<String> items) {
    }
}
