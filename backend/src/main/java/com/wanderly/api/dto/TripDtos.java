package com.wanderly.api.dto;

import com.wanderly.api.domain.Activity;
import com.wanderly.api.domain.Enums.ActivityType;
import com.wanderly.api.domain.ItineraryDay;
import com.wanderly.api.domain.Trip;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

/** JSON shapes mirror the Flutter `Trip`, `ItineraryDay` and `Activity` models. */
public final class TripDtos {

    private TripDtos() {
    }

    public record ActivityDto(String id, String title, String time, ActivityType type, String note, int cost) {
        public static ActivityDto from(Activity a) {
            return new ActivityDto(a.getId(), a.getTitle(), a.getTime(), a.getType(), a.getNote(), a.getCost());
        }
    }

    public record DayDto(int dayNumber, LocalDate date, List<ActivityDto> activities) {
        static DayDto from(ItineraryDay d, LocalDate start) {
            return new DayDto(d.getDayNumber(), start.plusDays(d.getDayNumber() - 1L),
                    d.getActivities().stream().map(ActivityDto::from).toList());
        }
    }

    public record TripResponse(String id, String destinationId, String destinationName, String title, String imageUrl,
                               LocalDate startDate, LocalDate endDate, int dayCount, int travelers, int budget,
                               int plannedCost, int activityCount, boolean upcoming, List<DayDto> days) {
        public static TripResponse from(Trip t) {
            var days = t.getDays().stream().map(d -> DayDto.from(d, t.getStartDate())).toList();
            int planned = days.stream().flatMap(d -> d.activities().stream()).mapToInt(ActivityDto::cost).sum();
            int count = days.stream().mapToInt(d -> d.activities().size()).sum();
            return new TripResponse(t.getId(), t.getDestination().getId(), t.getDestination().getName(), t.getTitle(),
                    t.getImageUrl(), t.getStartDate(), t.getEndDate(), t.dayCount(), t.getTravelers(), t.getBudget(),
                    planned, count, !t.getEndDate().isBefore(LocalDate.now()), days);
        }
    }

    public record ActivityRequest(
            @NotBlank @Size(max = 200) String title,
            @NotBlank @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "must be HH:mm") String time,
            @NotNull ActivityType type,
            @Size(max = 500) String note,
            @PositiveOrZero Integer cost) {

        public int costOrZero() {
            return cost == null ? 0 : cost;
        }
    }

    public record DayRequest(@Min(1) int dayNumber, @Valid List<ActivityRequest> activities) {
    }

    /**
     * Create a trip. {@code days} is optional — the AI planner sends a full
     * itinerary, the manual planner sends none and gets empty days.
     */
    public record CreateTripRequest(
            @NotBlank String destinationId,
            @Size(max = 150) String title,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Min(1) @Max(50) int travelers,
            @PositiveOrZero Integer budget,
            @Valid List<DayRequest> days) {
    }

    public record UpdateTripRequest(
            @NotBlank @Size(max = 150) String title,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Min(1) @Max(50) int travelers,
            @PositiveOrZero int budget) {
    }

    /** New order of activity ids for one day (drag-to-reorder). */
    public record ReorderRequest(@NotNull List<String> activityIds) {
    }
}
