package com.wanderly.api.controller;

import com.wanderly.api.domain.User;
import com.wanderly.api.dto.TripDtos.*;
import com.wanderly.api.service.TripService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripService service;

    public TripController(TripService service) {
        this.service = service;
    }

    /** GET /api/trips?status=upcoming|past */
    @GetMapping
    public List<TripResponse> list(@AuthenticationPrincipal User user, @RequestParam(required = false) String status) {
        return service.list(user, status);
    }

    @GetMapping("/{id}")
    public TripResponse get(@AuthenticationPrincipal User user, @PathVariable String id) {
        return service.get(user, id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripResponse create(@AuthenticationPrincipal User user, @Valid @RequestBody CreateTripRequest req) {
        return service.create(user, req);
    }

    @PutMapping("/{id}")
    public TripResponse update(@AuthenticationPrincipal User user, @PathVariable String id,
                               @Valid @RequestBody UpdateTripRequest req) {
        return service.update(user, id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal User user, @PathVariable String id) {
        service.delete(user, id);
    }

    @PostMapping("/{id}/days/{day}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityDto addActivity(@AuthenticationPrincipal User user, @PathVariable String id, @PathVariable int day,
                                   @Valid @RequestBody ActivityRequest req) {
        return service.addActivity(user, id, day, req);
    }

    @PutMapping("/{id}/days/{day}/activities/{activityId}")
    public ActivityDto updateActivity(@AuthenticationPrincipal User user, @PathVariable String id,
                                      @PathVariable int day, @PathVariable String activityId,
                                      @Valid @RequestBody ActivityRequest req) {
        return service.updateActivity(user, id, day, activityId, req);
    }

    @DeleteMapping("/{id}/days/{day}/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteActivity(@AuthenticationPrincipal User user, @PathVariable String id, @PathVariable int day,
                               @PathVariable String activityId) {
        service.deleteActivity(user, id, day, activityId);
    }

    /** Body: {"activityIds": ["id3", "id1", "id2"]} */
    @PutMapping("/{id}/days/{day}/activities/order")
    public TripResponse reorder(@AuthenticationPrincipal User user, @PathVariable String id, @PathVariable int day,
                                @Valid @RequestBody ReorderRequest req) {
        return service.reorder(user, id, day, req);
    }
}
