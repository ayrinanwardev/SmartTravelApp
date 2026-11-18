package com.wanderly.api.service;

import com.wanderly.api.domain.Attraction;
import com.wanderly.api.domain.CostEstimate;
import com.wanderly.api.domain.Destination;
import com.wanderly.api.domain.Enums.ActivityType;
import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.dto.DestinationDtos.DestinationSummary;
import com.wanderly.api.dto.TripDtos.ActivityRequest;
import com.wanderly.api.dto.TripDtos.DayRequest;
import com.wanderly.api.repository.DestinationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.NumberFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rule-based travel planner — the server-side version of the Flutter
 * {@code LocalAiTravelService}. Parses destination, length, travellers and
 * budget from free text and builds an itinerary + cost estimate.
 * <p>
 * The response shape is designed so an LLM-backed implementation can replace
 * this class later without changing the API.
 */
@Service
public class AiPlannerService {

    public record PlanRequest(String prompt) {
    }

    public record Plan(DestinationSummary destination, int days, int travelers, Integer requestedBudget,
                       int estimate, boolean fitsBudget, Map<String, Integer> breakdown,
                       List<DayRequest> itinerary, List<String> tips) {
    }

    public record PlanResponse(String reply, Plan plan, List<DestinationSummary> suggestions) {
    }

    private static final Map<String, String> ALIASES = Map.of(
            "coxs", "coxs-bazar", "cox", "coxs-bazar", "sreemangal", "sylhet", "nepal", "pokhara",
            "japan", "kyoto", "switzerland", "interlaken", "greece", "santorini");

    private static final Pattern DAYS = Pattern.compile("(\\d+)\\s*[- ]?\\s*(days?|nights?)");
    private static final Pattern TRAVELERS = Pattern.compile("(\\d+)\\s*(people|persons|travell?ers|friends|adults|of us)");
    private static final Pattern BUDGET_PREFIX = Pattern.compile(
            "(?:৳|tk\\.?|bdt|taka|under|budget|within|below)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k|lakh|lac)?");
    private static final Pattern BUDGET_SUFFIX = Pattern.compile("([\\d,]+(?:\\.\\d+)?)\\s*(k|lakh|lac)?\\s*(?:৳|tk|bdt|taka)");

    private final DestinationRepository destinations;

    public AiPlannerService(DestinationRepository destinations) {
        this.destinations = destinations;
    }

    @Transactional(readOnly = true)
    public PlanResponse plan(String prompt) {
        String p = prompt == null ? "" : prompt.toLowerCase(Locale.ROOT);
        List<Destination> all = destinations.findAll();

        Destination dest = match(p, all);
        if (dest == null) {
            var ideas = suggest(p, all);
            return new PlanResponse("I couldn't spot a destination in that. Try something like "
                    + "\"Plan a 4-day trip to Cox's Bazar under ৳20,000\". Some ideas: "
                    + String.join(", ", ideas.stream().map(DestinationSummary::name).toList()) + ".",
                    null, ideas);
        }

        int days = clamp(parseDays(p).orElse(3), 1, 14);
        int travelers = clamp(parseTravelers(p).orElse(1), 1, 20);
        Integer budget = parseBudget(p).orElse(null);
        Plan plan = build(dest, days, travelers, budget);

        var reply = new StringBuilder("Here is a ").append(days).append("-day plan for ").append(dest.getName());
        if (travelers > 1) reply.append(" for ").append(travelers).append(" travellers");
        reply.append(". Estimated cost is ৳").append(fmt(plan.estimate()));
        if (budget != null) {
            reply.append(plan.fitsBudget()
                    ? " — comfortably within your ৳" + fmt(budget) + " budget. 🎉"
                    : ". That is ৳" + fmt(plan.estimate() - budget) + " over your ৳" + fmt(budget)
                    + " budget, so I picked budget stays — you could also trim a day or travel by bus.");
        } else {
            reply.append('.');
        }
        return new PlanResponse(reply.toString(), plan, List.of());
    }

    // ---------------------------------------------------------------- parsing

    static Destination match(String p, List<Destination> all) {
        for (Destination d : all) {
            String name = d.getName().toLowerCase(Locale.ROOT);
            String simple = name.replace("'", "").replace("’", "");
            if (p.contains(name) || p.contains(simple) || p.contains(d.getId().replace('-', ' '))) return d;
        }
        for (var e : ALIASES.entrySet()) {
            if (p.contains(e.getKey())) {
                return all.stream().filter(d -> d.getId().equals(e.getValue())).findFirst().orElse(null);
            }
        }
        return null;
    }

    static Optional<Integer> parseDays(String p) {
        Matcher m = DAYS.matcher(p);
        if (m.find()) {
            int n = Integer.parseInt(m.group(1));
            return Optional.of(m.group(2).startsWith("night") ? n + 1 : n);
        }
        if (p.contains("weekend")) return Optional.of(2);
        if (p.contains("week")) return Optional.of(7);
        return Optional.empty();
    }

    static Optional<Integer> parseTravelers(String p) {
        Matcher m = TRAVELERS.matcher(p);
        if (m.find()) return Optional.of(Integer.parseInt(m.group(1)));
        if (p.contains("couple") || p.contains("honeymoon")) return Optional.of(2);
        if (p.contains("family")) return Optional.of(4);
        return Optional.empty();
    }

    static Optional<Integer> parseBudget(String p) {
        Matcher m = BUDGET_PREFIX.matcher(p);
        if (!m.find()) {
            m = BUDGET_SUFFIX.matcher(p);
            if (!m.find()) return Optional.empty();
        }
        double value;
        try {
            value = Double.parseDouble(m.group(1).replace(",", ""));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        String unit = m.group(2);
        if ("k".equals(unit)) value *= 1_000;
        else if ("lakh".equals(unit) || "lac".equals(unit)) value *= 100_000;
        return value < 500 ? Optional.empty() : Optional.of((int) Math.round(value));
    }

    private static List<DestinationSummary> suggest(String p, List<Destination> all) {
        for (DestinationCategory c : DestinationCategory.values()) {
            if (p.contains(c.json())) {
                return all.stream().filter(d -> d.getCategory() == c).limit(3).map(DestinationSummary::from).toList();
            }
        }
        return all.stream().sorted(Comparator.comparingDouble(Destination::getRating).reversed())
                .limit(3).map(DestinationSummary::from).toList();
    }

    // --------------------------------------------------------------- planning

    private static Plan build(Destination d, int days, int travelers, Integer budget) {
        CostEstimate c = d.getCost();
        int nights = Math.max(days - 1, 1);
        int rooms = (int) Math.ceil(travelers / 2.0);
        int hotelRate = c.getHotelPerNight();
        int fixed = c.getTransport() * travelers + (c.getFoodPerDay() + c.getActivitiesPerDay()) * days * travelers;
        if (budget != null && fixed + hotelRate * nights * rooms > budget) {
            hotelRate = (int) Math.round(hotelRate * 0.55); // downgrade the stay when over budget
        }

        var breakdown = new LinkedHashMap<String, Integer>();
        breakdown.put("Transport", c.getTransport() * travelers);
        breakdown.put("Stay", hotelRate * nights * rooms);
        breakdown.put("Food", c.getFoodPerDay() * days * travelers);
        breakdown.put("Activities", c.getActivitiesPerDay() * days * travelers);
        int estimate = breakdown.values().stream().mapToInt(Integer::intValue).sum();

        List<Attraction> attractions = d.getAttractions();
        var itinerary = new ArrayList<DayRequest>();
        for (int i = 0; i < days; i++) {
            var acts = new ArrayList<ActivityRequest>();
            boolean last = i == days - 1;
            acts.add(i == 0
                    ? new ActivityRequest("Arrive in " + d.getName() + " & check in", "11:00", ActivityType.STAY, "", 0)
                    : new ActivityRequest("Breakfast near your stay", "08:30", ActivityType.FOOD, "", 0));
            if (!attractions.isEmpty()) {
                Attraction a = attractions.get(i % attractions.size());
                boolean adventure = a.getType() != null && a.getType().toLowerCase(Locale.ROOT).contains("adventure");
                acts.add(new ActivityRequest(
                        i < attractions.size() ? "Explore " + a.getName() : "Revisit " + a.getName() + " at golden hour",
                        i == 0 ? "15:00" : "10:00",
                        adventure ? ActivityType.ADVENTURE : ActivityType.SIGHTSEEING,
                        a.getType(), (int) Math.round(c.getActivitiesPerDay() * 0.6)));
            }
            if (i > 0 && !last && i - 1 < d.getHighlights().size()) {
                acts.add(new ActivityRequest(d.getHighlights().get(i - 1), "14:00", ActivityType.ADVENTURE, "", 0));
            }
            acts.add(new ActivityRequest(
                    last ? "Souvenir shopping & departure" : "Dinner — try local specialities",
                    last ? "16:00" : "19:30",
                    last ? ActivityType.SHOPPING : ActivityType.FOOD, "", c.getFoodPerDay() / 2));
            itinerary.add(new DayRequest(i + 1, acts));
        }

        var tips = new ArrayList<String>();
        tips.add("Best time to visit: " + d.getBestTime() + ".");
        if (d.getAvgTempC() >= 26) tips.add("Pack light cotton, sunscreen and sunglasses — it averages " + d.getAvgTempC() + "°C.");
        if (d.getAvgTempC() < 18) tips.add("Bring warm layers — average temperature is " + d.getAvgTempC() + "°C.");
        if (budget != null && estimate > budget) tips.add("Travel mid-week and book stays 3+ weeks ahead to save 15–25%.");
        if (travelers > 2) tips.add("Split a private car between " + travelers + " people — often cheaper than individual tickets.");

        return new Plan(DestinationSummary.from(d), days, travelers, budget, estimate,
                budget == null || estimate <= budget, breakdown, itinerary, tips);
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private static String fmt(int v) {
        return NumberFormat.getIntegerInstance(Locale.US).format(v);
    }
}
