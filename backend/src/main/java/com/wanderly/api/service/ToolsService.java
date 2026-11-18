package com.wanderly.api.service;

import com.wanderly.api.domain.Destination;
import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.Season;
import com.wanderly.api.dto.CommonDtos.ConversionResult;
import com.wanderly.api.dto.DestinationDtos.PackingList;
import com.wanderly.api.dto.DestinationDtos.PackingSection;
import com.wanderly.api.exception.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Currency converter and smart packing list. */
@Service
public class ToolsService {

    /**
     * 1 unit of currency = x BDT. Static demo rates — replace with a live
     * exchange-rate API (and cache it) for production.
     */
    public static final Map<String, Double> BDT_RATES;

    static {
        var m = new LinkedHashMap<String, Double>();
        m.put("BDT", 1.0);
        m.put("USD", 122.0);
        m.put("EUR", 132.5);
        m.put("GBP", 155.0);
        m.put("CAD", 88.5);
        m.put("AUD", 80.0);
        m.put("INR", 1.45);
        m.put("JPY", 0.81);
        m.put("AED", 33.2);
        m.put("SGD", 91.0);
        m.put("MYR", 27.5);
        BDT_RATES = java.util.Collections.unmodifiableMap(m);
    }

    private final DestinationService destinations;

    public ToolsService(DestinationService destinations) {
        this.destinations = destinations;
    }

    public ConversionResult convert(String from, String to, double amount) {
        Double f = BDT_RATES.get(from.toUpperCase());
        Double t = BDT_RATES.get(to.toUpperCase());
        if (f == null || t == null) throw ApiException.badRequest("Supported currencies: " + BDT_RATES.keySet());
        double rate = f / t;
        return new ConversionResult(from.toUpperCase(), to.toUpperCase(), amount,
                Math.round(amount * rate * 100) / 100.0, Math.round(rate * 1_000_000) / 1_000_000.0);
    }

    @Transactional(readOnly = true)
    public PackingList packing(String destinationId, int days) {
        if (days < 1 || days > 60) throw ApiException.badRequest("days must be between 1 and 60");
        Destination d = destinations.find(destinationId);
        int outfits = Math.max(2, Math.min(10, (int) Math.ceil(days * 0.8)));
        boolean hot = d.getAvgTempC() >= 25;
        boolean cold = d.getAvgTempC() <= 16;
        boolean abroad = !"Bangladesh".equalsIgnoreCase(d.getCountry());
        boolean rainy = d.getBestSeasons().contains(Season.MONSOON);
        var cat = d.getCategory();

        var documents = new ArrayList<String>();
        if (abroad) documents.addAll(List.of("Passport (6+ months validity)", "Visa / e-visa printout", "Travel insurance"));
        else documents.add("National ID card");
        documents.addAll(List.of("Booking confirmations", "Emergency contacts"));

        var clothing = new ArrayList<>(List.of(outfits + " × tops", (int) Math.ceil(outfits / 2.0) + " × bottoms",
                days + " × underwear & socks", "Sleepwear"));
        if (hot) clothing.addAll(List.of("Light cotton shirts", "Sun hat", "Sunglasses"));
        if (cold) clothing.addAll(List.of("Warm jacket", "Thermal layer", "Beanie & gloves"));
        if (rainy) clothing.add("Rain jacket / poncho");
        if (cat == DestinationCategory.BEACH) clothing.addAll(List.of("Swimwear", "Flip-flops", "Beach towel"));
        if (cat == DestinationCategory.MOUNTAIN || cat == DestinationCategory.ADVENTURE)
            clothing.addAll(List.of("Hiking shoes", "Quick-dry trousers"));
        if (cat == DestinationCategory.CITY || cat == DestinationCategory.CULTURE)
            clothing.addAll(List.of("Comfortable walking shoes", "One smart outfit"));

        var toiletries = new ArrayList<>(List.of("Toothbrush & toothpaste", "Shampoo & soap"));
        if (hot || cat == DestinationCategory.BEACH) toiletries.add("Sunscreen SPF 50");
        toiletries.addAll(List.of("Insect repellent", "Personal medication", "Basic first-aid kit"));

        var electronics = new ArrayList<>(List.of("Phone & charger", "Power bank"));
        if (abroad) electronics.add("Universal travel adapter");
        if (cat == DestinationCategory.BEACH) electronics.add("Waterproof phone pouch");
        electronics.add("Earphones");

        var extras = new ArrayList<>(List.of("Reusable water bottle", "Day backpack",
                abroad ? "Some local cash (" + d.getCountry() + ")" : "Cash for local markets", "Snacks for the journey"));

        return new PackingList(d.getId(), days, List.of(
                new PackingSection("Documents", documents),
                new PackingSection("Clothing", clothing),
                new PackingSection("Toiletries", toiletries),
                new PackingSection("Electronics", electronics),
                new PackingSection("Extras", extras)));
    }
}
