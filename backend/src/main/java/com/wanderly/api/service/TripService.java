package com.wanderly.api.service;

import com.wanderly.api.domain.*;
import com.wanderly.api.domain.Enums.NotificationType;
import com.wanderly.api.dto.TripDtos.*;
import com.wanderly.api.exception.ApiException;
import com.wanderly.api.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

@Service
public class TripService {

    private static final int MAX_DAYS = 60;

    private final TripRepository trips;
    private final DestinationService destinations;
    private final NotificationService notifications;

    public TripService(TripRepository trips, DestinationService destinations, NotificationService notifications) {
        this.trips = trips;
        this.destinations = destinations;
        this.notifications = notifications;
    }

    @Transactional(readOnly = true)
    public List<TripResponse> list(User user, String status) {
        var today = LocalDate.now();
        return trips.findByUserIdOrderByStartDateAsc(user.getId()).stream()
                .filter(t -> status == null
                        || ("upcoming".equalsIgnoreCase(status) && !t.getEndDate().isBefore(today))
                        || ("past".equalsIgnoreCase(status) && t.getEndDate().isBefore(today)))
                .map(TripResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public TripResponse get(User user, String id) {
        return TripResponse.from(find(user, id));
    }

    @Transactional
    public TripResponse create(User user, CreateTripRequest req) {
        validateDates(req.startDate(), req.endDate());
        Destination dest = destinations.find(req.destinationId());
        int days = (int) ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1;
        int budget = req.budget() != null ? req.budget() : dest.getCost().forDays(days, req.travelers());
        String title = req.title() == null || req.title().isBlank() ? dest.getName() + " Trip" : req.title().trim();

        Trip trip = new Trip(user, dest, title, req.startDate(), req.endDate(), req.travelers(), budget);
        trip.syncDays();
        if (req.days() != null) {
            for (DayRequest d : req.days()) {
                ItineraryDay day = trip.day(d.dayNumber());
                if (day == null || d.activities() == null) continue;
                d.activities().forEach(a -> day.addActivity(toActivity(a)));
            }
        }
        trip = trips.save(trip);
        notifications.notify(user, NotificationType.TRIP, "Trip created: " + title,
                days + "-day trip to " + dest.getName() + " starting " + req.startDate() + ".");
        return TripResponse.from(trip);
    }

    @Transactional
    public TripResponse update(User user, String id, UpdateTripRequest req) {
        validateDates(req.startDate(), req.endDate());
        Trip trip = find(user, id);
        trip.setTitle(req.title().trim());
        trip.setStartDate(req.startDate());
        trip.setEndDate(req.endDate());
        trip.setTravelers(req.travelers());
        trip.setBudget(req.budget());
        trip.syncDays();
        return TripResponse.from(trips.saveAndFlush(trip));
    }

    @Transactional
    public void delete(User user, String id) {
        trips.delete(find(user, id));
    }

    @Transactional
    public ActivityDto addActivity(User user, String tripId, int dayNumber, ActivityRequest req) {
        ItineraryDay day = findDay(find(user, tripId), dayNumber);
        Activity activity = toActivity(req);
        day.addActivity(activity);
        trips.flush();
        return ActivityDto.from(activity);
    }

    @Transactional
    public ActivityDto updateActivity(User user, String tripId, int dayNumber, String activityId, ActivityRequest req) {
        Activity a = findActivity(findDay(find(user, tripId), dayNumber), activityId);
        a.update(req.title().trim(), req.time(), req.type(), req.note(), req.costOrZero());
        return ActivityDto.from(a);
    }

    @Transactional
    public void deleteActivity(User user, String tripId, int dayNumber, String activityId) {
        ItineraryDay day = findDay(find(user, tripId), dayNumber);
        day.getActivities().remove(findActivity(day, activityId));
    }

    /** Replaces the order of a day's activities. The list must contain exactly the day's activity ids. */
    @Transactional
    public TripResponse reorder(User user, String tripId, int dayNumber, ReorderRequest req) {
        Trip trip = find(user, tripId);
        ItineraryDay day = findDay(trip, dayNumber);
        var current = day.getActivities();
        var ids = req.activityIds();
        var currentIds = current.stream().map(Activity::getId).toList();
        if (ids.size() != current.size() || !new HashSet<>(ids).equals(new HashSet<>(currentIds))) {
            throw ApiException.badRequest("activityIds must contain each activity of day " + dayNumber + " exactly once");
        }
        var reordered = new ArrayList<Activity>();
        for (String id : ids) reordered.add(findActivity(day, id));
        current.clear();
        current.addAll(reordered);
        return TripResponse.from(trips.saveAndFlush(trip));
    }

    Trip find(User user, String id) {
        return trips.findByIdAndUserId(id, user.getId()).orElseThrow(() -> ApiException.notFound("Trip", id));
    }

    private static ItineraryDay findDay(Trip trip, int dayNumber) {
        ItineraryDay day = trip.day(dayNumber);
        if (day == null) throw ApiException.notFound("Day", dayNumber);
        return day;
    }

    private static Activity findActivity(ItineraryDay day, String activityId) {
        return day.getActivities().stream().filter(a -> a.getId().equals(activityId)).findFirst()
                .orElseThrow(() -> ApiException.notFound("Activity", activityId));
    }

    private static Activity toActivity(ActivityRequest a) {
        return new Activity(a.title().trim(), a.time(), a.type(), a.note(), a.costOrZero());
    }

    private static void validateDates(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) throw ApiException.badRequest("endDate must be on or after startDate");
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest("Trips are limited to " + MAX_DAYS + " days");
        }
    }
}
