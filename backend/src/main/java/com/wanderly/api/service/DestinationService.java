package com.wanderly.api.service;

import com.wanderly.api.domain.Destination;
import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.Season;
import com.wanderly.api.domain.Review;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.DestinationDtos.*;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class DestinationService {

    public enum Sort { RATING, PRICE_LOW, PRICE_HIGH }

    /** Filters for GET /api/destinations. All are optional. */
    public record Filter(String q, DestinationCategory category, Season season, Integer maxBudget,
                         Boolean trending, Boolean popular, Sort sort) {
    }

    private final DestinationRepository repo;

    public DestinationService(DestinationRepository repo) {
        this.repo = repo;
    }

    /**
     * The catalogue is small (tens of rows), so filtering happens in memory.
     * Move to JPA Specifications + paging if it grows to thousands.
     */
    @Transactional(readOnly = true)
    public List<DestinationSummary> search(Filter f) {
        return filter(f).stream().map(DestinationSummary::from).toList();
    }

    @Transactional(readOnly = true)
    public List<DestinationDetail> searchFull(Filter f) {
        return filter(f).stream().map(DestinationDetail::from).toList();
    }

    private List<Destination> filter(Filter f) {
        String q = f.q() == null ? "" : f.q().trim().toLowerCase(Locale.ROOT);
        Comparator<Destination> order = switch (f.sort() == null ? Sort.RATING : f.sort()) {
            case RATING -> Comparator.comparingDouble(Destination::getRating).reversed();
            case PRICE_LOW -> Comparator.comparingInt(Destination::typicalTripCost);
            case PRICE_HIGH -> Comparator.comparingInt(Destination::typicalTripCost).reversed();
        };
        return repo.findAll().stream()
                .filter(d -> q.isEmpty()
                        || d.getName().toLowerCase(Locale.ROOT).contains(q)
                        || d.getCountry().toLowerCase(Locale.ROOT).contains(q)
                        || (d.getTagline() != null && d.getTagline().toLowerCase(Locale.ROOT).contains(q)))
                .filter(d -> f.category() == null || d.getCategory() == f.category())
                .filter(d -> f.season() == null || d.getBestSeasons().contains(f.season()))
                .filter(d -> f.maxBudget() == null || d.typicalTripCost() <= f.maxBudget())
                .filter(d -> f.trending() == null || d.isTrending() == f.trending())
                .filter(d -> f.popular() == null || d.isPopular() == f.popular())
                .sorted(order)
                .toList();
    }

    @Transactional(readOnly = true)
    public DestinationDetail get(String id) {
        return DestinationDetail.from(find(id));
    }

    @Transactional
    public ReviewDto addReview(String id, User user, ReviewRequest req) {
        Destination d = find(id);
        var review = new Review(user.getName(), req.rating(), req.comment().trim(), LocalDate.now());
        review.setUser(user);
        d.addReview(review);
        repo.saveAndFlush(d);
        return ReviewDto.from(review);
    }

    /** Simulated 5-day forecast + activity suggestion (swap for a real weather API). */
    @Transactional(readOnly = true)
    public WeatherForecast weather(String id) {
        Destination d = find(id);
        int seed = d.getId().chars().sum();
        var days = new ArrayList<WeatherDay>();
        int rainy = 0;
        for (int i = 0; i < 5; i++) {
            int temp = d.getAvgTempC() + ((seed + i * 5) % 7) - 3;
            boolean rain = (seed + i * 3) % 5 == 0;
            boolean cloudy = !rain && (seed + i) % 3 == 0;
            if (rain) rainy++;
            days.add(new WeatherDay(LocalDate.now().plusDays(i), temp, rain ? "rain" : cloudy ? "cloudy" : "sunny"));
        }
        String suggestion;
        if (rainy >= 2) {
            suggestion = "Rain expected on " + rainy + " days — plan museums, cafés and spa time for wet afternoons.";
        } else if (d.getAvgTempC() >= 26) {
            suggestion = "Hot & sunny — do outdoor sightseeing before 11am and save sunsets for the beach.";
        } else if (d.getAvgTempC() <= 15) {
            suggestion = "Cool weather — perfect for hiking and city walks. Pack warm layers.";
        } else {
            suggestion = "Pleasant conditions — great for outdoor activities all day.";
        }
        return new WeatherForecast(d.getId(), days, suggestion);
    }

    Destination find(String id) {
        return repo.findById(id).orElseThrow(() -> ApiException.notFound("Destination", id));
    }
}
