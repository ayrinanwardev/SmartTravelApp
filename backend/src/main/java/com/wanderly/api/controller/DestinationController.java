package com.wanderly.api.controller;

import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.Season;
import com.wanderly.api.domain.User;
import com.wanderly.api.dto.DestinationDtos.*;
import com.wanderly.api.service.DestinationService;
import com.wanderly.api.service.DestinationService.Filter;
import com.wanderly.api.service.DestinationService.Sort;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequestMapping("/api/destinations")
public class DestinationController {

    private final DestinationService service;

    public DestinationController(DestinationService service) {
        this.service = service;
    }

    /**
     * GET /api/destinations?q=bali&category=beach&season=winter&maxBudget=30000&sort=price_low
     * <p>
     * With {@code full=true} each item is a full {@link DestinationDetail} (the Flutter app loads
     * the whole catalogue once this way); otherwise lightweight {@link DestinationSummary} cards.
     */
    @GetMapping
    public List<?> search(@RequestParam(required = false) String q,
                                           @RequestParam(required = false) String category,
                                           @RequestParam(required = false) String season,
                                           @RequestParam(required = false) Integer maxBudget,
                                           @RequestParam(required = false) Boolean trending,
                                           @RequestParam(required = false) Boolean popular,
                                           @RequestParam(required = false, defaultValue = "rating") String sort,
                                           @RequestParam(required = false, defaultValue = "false") boolean full) {
        var filter = new Filter(q,
                category == null ? null : DestinationCategory.valueOf(category.toUpperCase(Locale.ROOT)),
                season == null ? null : Season.valueOf(season.toUpperCase(Locale.ROOT)),
                maxBudget, trending, popular, Sort.valueOf(sort.toUpperCase(Locale.ROOT)));
        return full ? service.searchFull(filter) : service.search(filter);
    }

    @GetMapping("/{id}")
    public DestinationDetail get(@PathVariable String id) {
        return service.get(id);
    }

    @GetMapping("/{id}/weather")
    public WeatherForecast weather(@PathVariable String id) {
        return service.weather(id);
    }

    @PostMapping("/{id}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewDto addReview(@PathVariable String id, @AuthenticationPrincipal User user,
                               @Valid @RequestBody ReviewRequest req) {
        return service.addReview(id, user, req);
    }
}
