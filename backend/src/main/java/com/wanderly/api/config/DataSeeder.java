package com.wanderly.api.config;

import com.wanderly.api.domain.*;
import com.wanderly.api.domain.Enums.ActivityType;
import com.wanderly.api.domain.Enums.DestinationCategory;
import com.wanderly.api.domain.Enums.ExpenseCategory;
import com.wanderly.api.domain.Enums.Season;
import com.wanderly.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

import static com.wanderly.api.domain.Enums.DestinationCategory.*;
import static com.wanderly.api.domain.Enums.Season.*;

/**
 * Loads the same catalogue as the Flutter app's MockData on first start
 * (only when the destinations table is empty), plus a demo account:
 * demo@wanderly.app / wanderly123
 */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    public static final String DEMO_EMAIL = "demo@wanderly.app";
    public static final String DEMO_PASSWORD = "wanderly123";

    private final WanderlyProperties props;
    private final DestinationRepository destinations;
    private final HotelRepository hotels;
    private final UserRepository users;
    private final TripRepository trips;
    private final ExpenseRepository expenses;
    private final PasswordEncoder encoder;

    public DataSeeder(WanderlyProperties props, DestinationRepository destinations, HotelRepository hotels,
                      UserRepository users, TripRepository trips, ExpenseRepository expenses, PasswordEncoder encoder) {
        this.props = props;
        this.destinations = destinations;
        this.hotels = hotels;
        this.users = users;
        this.trips = trips;
        this.expenses = expenses;
        this.encoder = encoder;
    }

    // ------------------------------------------------------------------ images

    private static String img(String id) {
        return "https://images.unsplash.com/" + id + "?auto=format&fit=crop&w=1000&q=80";
    }

    private static final String BEACH = img("photo-1507525428034-b723cf961d3e");
    private static final String BEACH2 = img("photo-1506929562872-bb421503ef21");
    private static final String CLOUDS = img("photo-1464822759023-fed622ff2c3b");
    private static final String HILLS = img("photo-1433086966358-54859d0ed716");
    private static final String DESERT = img("photo-1500530855697-b586d89ba3ee");
    private static final String FOREST = img("photo-1448375240586-882707db888b");
    private static final String BALI = img("photo-1537996194471-e657df975ab4");
    private static final String MALDIVES = img("photo-1514282401047-d79a71a590e8");
    private static final String KYOTO = img("photo-1493976040374-85c8e12f0c0e");
    private static final String TOKYO = img("photo-1540959733332-eab4deabeeaf");
    private static final String PARIS = img("photo-1502602898657-3e91760cbb34");
    private static final String ALPS = img("photo-1476514525535-07fb3b4ae5f1");
    private static final String DUBAI = img("photo-1512453979798-5ea266f8880c");
    private static final String SANTORINI = img("photo-1570077188670-e3a8d69ac5ff");
    private static final String NEPAL = img("photo-1544735716-392fe2489ffa");
    private static final String VENICE = img("photo-1523906834658-6e24ef2386f9");
    private static final String LAKE = img("photo-1501785888041-af3ef285b470");
    private static final String ROAD = img("photo-1469474968028-56623f02e42e");
    private static final String H_POOL = img("photo-1566073771259-6a8506099945");
    private static final String H_LOBBY = img("photo-1551882547-ff40c63fe5fa");
    private static final String RESORT = img("photo-1520250497591-112f2f40a3f4");
    private static final String H_ROOM = img("photo-1542314831-068cd1dbfeeb");
    private static final String H_SUITE = img("photo-1582719478250-c89cae4dc85b");
    private static final String H_BED = img("photo-1571896349842-33c89424de2d");

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled() || destinations.count() > 0) return;
        seedDestinations();
        seedHotels();
        seedDemoUser();
        log.info("Seeded {} destinations, {} hotels and demo user {}", destinations.count(), hotels.count(), DEMO_EMAIL);
    }

    // ------------------------------------------------------------ destinations

    private Destination dest(String id, String name, String country, DestinationCategory cat, String tagline,
                             String description, String image, List<String> gallery, List<Season> seasons,
                             String bestTime, double rating, int reviews, int temp, boolean trending, boolean popular,
                             CostEstimate cost, List<String> highlights) {
        var d = new Destination(id, name, country, cat);
        d.setTagline(tagline);
        d.setDescription(description);
        d.setImageUrl(image);
        d.getGallery().addAll(gallery);
        d.getBestSeasons().addAll(seasons);
        d.setBestTime(bestTime);
        d.setRating(rating);
        d.setReviewCount(reviews);
        d.setAvgTempC(temp);
        d.setTrending(trending);
        d.setPopular(popular);
        d.setCost(cost);
        d.getHighlights().addAll(highlights);
        return d;
    }

    private static Review review(String author, double rating, String comment, int y, int m, int day) {
        return new Review(author, rating, comment, LocalDate.of(y, m, day));
    }

    /** Adds seeded reviews without changing the curated rating / review count. */
    private static void seedReviews(Destination d, Review... reviews) {
        double rating = d.getRating();
        int count = d.getReviewCount();
        for (Review r : reviews) d.addReview(r);
        d.setRating(rating);
        d.setReviewCount(count);
    }

    private void seedDestinations() {
        var coxs = dest("coxs-bazar", "Cox's Bazar", "Bangladesh", DestinationCategory.BEACH, "The world's longest natural sea beach",
                "Stretching 120 km along the Bay of Bengal, Cox's Bazar is an unbroken ribbon of golden sand, fishing "
                        + "villages and fiery sunsets. Ride the Marine Drive to Himchari waterfalls, hop to St. Martin's "
                        + "coral island, and end every evening with fresh seafood on Laboni Beach.",
                Img.BEACH, List.of(Img.BEACH, Img.BEACH2, Img.MALDIVES), List.of(WINTER, SPRING), "November – March",
                4.7, 2841, 26, true, true, new CostEstimate(3500, 4500, 1200, 800),
                List.of("120 km sea beach", "Marine Drive road", "Fresh seafood", "St. Martin's day trip"));
        coxs.addAttraction(new Attraction("Laboni Beach", "Beach", Img.BEACH2, 4.6, 1.2));
        coxs.addAttraction(new Attraction("Himchari National Park", "Nature", Img.FOREST, 4.5, 12));
        coxs.addAttraction(new Attraction("Inani Beach", "Beach", Img.BEACH, 4.8, 26));
        seedReviews(coxs,
                review("Nusrat J.", 5, "Sunsets on Marine Drive are unreal. Go in December!", 2026, 1, 12),
                review("Tanvir A.", 4.5, "Great food and very affordable. Inani is calmer than Laboni.", 2025, 12, 3));

        var sajek = dest("sajek", "Sajek Valley", "Bangladesh", MOUNTAIN, "Wake up above a sea of clouds",
                "Perched on the hills of Rangamati, Sajek is famous for dawn views over rolling clouds, colourful "
                        + "indigenous villages and cosy wooden cottages. A jeep ride from Khagrachari winds through jungle "
                        + "and tea gardens before reaching Konglak Para, the highest point.",
                Img.CLOUDS, List.of(Img.CLOUDS, Img.HILLS, Img.NEPAL), List.of(MONSOON, AUTUMN), "June – November",
                4.8, 1920, 22, true, false, new CostEstimate(2800, 3500, 900, 600),
                List.of("Sunrise above clouds", "Konglak Para hike", "Indigenous culture", "Bamboo chicken"));
        sajek.addAttraction(new Attraction("Konglak Para", "Viewpoint", Img.CLOUDS, 4.9, 2));
        sajek.addAttraction(new Attraction("Ruilui Para", "Village", Img.HILLS, 4.4, 0.5));
        sajek.addAttraction(new Attraction("Hazachora Waterfall", "Waterfall", Img.FOREST, 4.5, 18));
        seedReviews(sajek,
                review("Farhan R.", 5, "The cloud view at 6am is worth every bumpy jeep minute.", 2025, 9, 18),
                review("Mim S.", 4.5, "Book cottages early during holidays.", 2025, 8, 2));

        var sylhet = dest("sylhet", "Sylhet", "Bangladesh", ADVENTURE, "Tea gardens, swamp forests and crystal rivers",
                "Endless emerald tea estates roll across Sreemangal and Sylhet. Paddle through Ratargul, the only "
                        + "freshwater swamp forest in the country, then cool off in the crystal stones of Bholaganj and "
                        + "Jaflong right on the Meghalaya border.",
                Img.HILLS, List.of(Img.HILLS, Img.FOREST, Img.LAKE), List.of(MONSOON, AUTUMN), "June – October",
                4.6, 1504, 25, false, true, new CostEstimate(1500, 3000, 900, 700),
                List.of("Ratargul swamp boat ride", "Seven-layer tea", "Jaflong stones", "Lalakhal blue river"));
        sylhet.addAttraction(new Attraction("Ratargul Swamp Forest", "Nature", Img.FOREST, 4.7, 26));
        sylhet.addAttraction(new Attraction("Jaflong", "River", Img.LAKE, 4.5, 60));
        sylhet.addAttraction(new Attraction("Lakkatura Tea Estate", "Tea garden", Img.HILLS, 4.6, 4));
        seedReviews(sylhet, review("Arif H.", 4.5, "Ratargul in monsoon is magical.", 2025, 7, 22));

        var sundarbans = dest("sundarbans", "Sundarbans", "Bangladesh", ADVENTURE, "Home of the Royal Bengal Tiger",
                "The largest mangrove forest on Earth is a maze of tidal rivers and islands. Multi-day boat safaris glide "
                        + "past spotted deer, crocodiles and kingfishers, with a lucky few spotting the elusive Royal "
                        + "Bengal Tiger at Kotka or Kochikhali.",
                Img.FOREST, List.of(Img.FOREST, Img.LAKE, Img.HILLS), List.of(WINTER), "November – February",
                4.7, 980, 24, false, false, new CostEstimate(2500, 5500, 800, 1500),
                List.of("3-day boat safari", "UNESCO World Heritage", "Wildlife watching", "Karamjal centre"));
        sundarbans.addAttraction(new Attraction("Kotka Beach", "Wildlife", Img.FOREST, 4.6, 0));
        sundarbans.addAttraction(new Attraction("Karamjal", "Eco park", Img.LAKE, 4.3, 0));
        seedReviews(sundarbans,
                review("Sadia K.", 5, "Saw deer and a crocodile! Choose a licensed tour operator.", 2026, 1, 28));

        var bali = dest("bali", "Bali", "Indonesia", DestinationCategory.BEACH, "Island of the Gods",
                "Terraced rice fields, cliff-top temples and world-class surf. Base yourself in Ubud for jungle, yoga and "
                        + "culture, then head to Uluwatu and Canggu for beaches and sunset beach clubs.",
                Img.BALI, List.of(Img.BALI, Img.BEACH, Img.RESORT), List.of(SPRING, SUMMER), "April – October",
                4.8, 12840, 28, true, true, new CostEstimate(48000, 7500, 2500, 3000),
                List.of("Tegallalang rice terraces", "Uluwatu temple", "Surfing", "Balinese cuisine"));
        bali.addAttraction(new Attraction("Tegallalang Rice Terrace", "Nature", Img.BALI, 4.7, 10));
        bali.addAttraction(new Attraction("Uluwatu Temple", "Temple", Img.BEACH2, 4.8, 45));
        bali.addAttraction(new Attraction("Sacred Monkey Forest", "Nature", Img.FOREST, 4.5, 2));
        seedReviews(bali,
                review("Emma L.", 5, "Ubud stole my heart. Rent a scooter!", 2026, 5, 10),
                review("Rahim U.", 4.5, "Great value — villas with pools for hostel prices.", 2026, 3, 4));

        var maldives = dest("maldives", "Maldives", "Maldives", DestinationCategory.BEACH, "Overwater villas and turquoise lagoons",
                "1,192 coral islands floating in the Indian Ocean. Snorkel with manta rays, sleep above the lagoon, or "
                        + "stay on a local island like Maafushi for a budget-friendly paradise.",
                Img.MALDIVES, List.of(Img.MALDIVES, Img.BEACH, Img.RESORT), List.of(WINTER, SPRING), "December – April",
                4.9, 8410, 29, false, true, new CostEstimate(42000, 18000, 4000, 6000),
                List.of("Overwater villas", "Manta ray snorkelling", "Sandbank picnics", "Bioluminescent beach"));
        maldives.addAttraction(new Attraction("Maafushi Island", "Island", Img.BEACH2, 4.6, 0));
        maldives.addAttraction(new Attraction("Banana Reef", "Dive site", Img.MALDIVES, 4.8, 0));
        seedReviews(maldives, review("Sara M.", 5, "Clearest water I have ever seen.", 2026, 2, 14));

        var kyoto = dest("kyoto", "Kyoto", "Japan", CULTURE, "Temples, tea houses and cherry blossoms",
                "Japan's ancient capital is home to 1,600 temples, geisha districts and zen gardens. Walk the vermilion "
                        + "torii gates of Fushimi Inari at dawn and get lost in the bamboo groves of Arashiyama.",
                Img.KYOTO, List.of(Img.KYOTO, Img.TOKYO, Img.FOREST), List.of(SPRING, AUTUMN), "March – May, Oct – Nov",
                4.9, 15230, 16, true, false, new CostEstimate(85000, 11000, 4500, 2500),
                List.of("Fushimi Inari", "Arashiyama bamboo", "Gion district", "Kaiseki dining"));
        kyoto.addAttraction(new Attraction("Fushimi Inari Taisha", "Shrine", Img.KYOTO, 4.9, 5));
        kyoto.addAttraction(new Attraction("Arashiyama Bamboo Grove", "Nature", Img.FOREST, 4.6, 9));
        kyoto.addAttraction(new Attraction("Kinkaku-ji", "Temple", Img.LAKE, 4.8, 7));
        seedReviews(kyoto, review("Kenji T.", 5, "Go to Fushimi Inari before 7am for empty paths.", 2026, 4, 2));

        var paris = dest("paris", "Paris", "France", CITY, "The city of light",
                "Boulevards, bistros and the world's greatest museums. Picnic under the Eiffel Tower, browse Montmartre's "
                        + "artists and cruise the Seine at golden hour.",
                Img.PARIS, List.of(Img.PARIS, Img.VENICE, Img.H_LOBBY), List.of(SPRING, AUTUMN), "April – June, Sep – Oct",
                4.7, 22100, 15, false, true, new CostEstimate(95000, 16000, 6000, 4000),
                List.of("Eiffel Tower", "Louvre Museum", "Seine river cruise", "Montmartre"));
        paris.addAttraction(new Attraction("Eiffel Tower", "Landmark", Img.PARIS, 4.8, 3));
        paris.addAttraction(new Attraction("Louvre Museum", "Museum", Img.H_LOBBY, 4.8, 2));
        seedReviews(paris, review("Chloé D.", 4.5, "Buy museum tickets online to skip the queues.", 2026, 6, 20));

        var interlaken = dest("interlaken", "Interlaken", "Switzerland", MOUNTAIN, "Adventure capital of the Alps",
                "Sandwiched between two glacial lakes beneath the Eiger, Mönch and Jungfrau. Paraglide over emerald water, "
                        + "ride cogwheel trains to the Top of Europe and hike flower-filled valleys.",
                Img.ALPS, List.of(Img.ALPS, Img.LAKE, Img.CLOUDS), List.of(SUMMER, WINTER), "June – September",
                4.9, 6720, 12, false, false, new CostEstimate(105000, 20000, 7000, 9000),
                List.of("Jungfraujoch", "Paragliding", "Lake Brienz", "Lauterbrunnen valley"));
        interlaken.addAttraction(new Attraction("Jungfraujoch", "Mountain", Img.CLOUDS, 4.8, 25));
        interlaken.addAttraction(new Attraction("Lake Brienz", "Lake", Img.LAKE, 4.9, 3));
        seedReviews(interlaken, review("Lukas B.", 5, "Get the Berner Oberland pass — it saves a lot.", 2025, 8, 9));

        var dubai = dest("dubai", "Dubai", "UAE", CITY, "Skylines, souks and desert dunes",
                "Futuristic towers rise beside traditional souks. Watch the city from the Burj Khalifa, bash dunes on a "
                        + "desert safari and shop until midnight.",
                Img.DUBAI, List.of(Img.DUBAI, Img.H_POOL, Img.H_SUITE), List.of(WINTER), "November – March",
                4.6, 18300, 30, true, false, new CostEstimate(45000, 12000, 4000, 5000),
                List.of("Burj Khalifa", "Desert safari", "Gold souk", "Dubai Marina"));
        dubai.addAttraction(new Attraction("Burj Khalifa", "Landmark", Img.DUBAI, 4.7, 1));
        dubai.addAttraction(new Attraction("Desert Safari", "Adventure", Img.DESERT, 4.6, 45));
        seedReviews(dubai, review("Omar F.", 4.5, "Desert safari was the highlight.", 2026, 1, 3));

        var santorini = dest("santorini", "Santorini", "Greece", DestinationCategory.BEACH, "Whitewashed cliffs above the Aegean",
                "Blue-domed churches cling to volcanic cliffs above a sunken caldera. Watch the famous Oia sunset, swim at "
                        + "red and black sand beaches and taste wines grown in volcanic soil.",
                Img.SANTORINI, List.of(Img.SANTORINI, Img.BEACH2, Img.H_POOL), List.of(SPRING, SUMMER), "May – October",
                4.8, 9900, 24, false, false, new CostEstimate(98000, 17000, 5500, 4000),
                List.of("Oia sunset", "Caldera cruise", "Red Beach", "Wine tasting"));
        santorini.addAttraction(new Attraction("Oia Village", "Village", Img.SANTORINI, 4.9, 0));
        santorini.addAttraction(new Attraction("Red Beach", "Beach", Img.BEACH2, 4.4, 0));
        seedReviews(santorini, review("Anna P.", 5, "Stay in Imerovigli for the views without the crowds.", 2025, 9, 1));

        var pokhara = dest("pokhara", "Pokhara", "Nepal", MOUNTAIN, "Gateway to the Annapurnas",
                "A laid-back lakeside town with the Annapurna range mirrored on Phewa Lake. Paraglide from Sarangkot, trek "
                        + "to Poon Hill, and row out to the Tal Barahi temple.",
                Img.NEPAL, List.of(Img.NEPAL, Img.CLOUDS, Img.LAKE), List.of(AUTUMN, SPRING), "October – April",
                4.7, 4300, 19, false, true, new CostEstimate(22000, 4000, 1500, 2500),
                List.of("Sarangkot sunrise", "Phewa Lake", "Paragliding", "Poon Hill trek"));
        pokhara.addAttraction(new Attraction("Sarangkot", "Viewpoint", Img.NEPAL, 4.8, 8));
        pokhara.addAttraction(new Attraction("Phewa Lake", "Lake", Img.LAKE, 4.7, 1));
        seedReviews(pokhara,
                review("Imran K.", 4.5, "Perfect first trip abroad from Dhaka — affordable and beautiful.", 2025, 11, 11));

        destinations.saveAll(List.of(coxs, sajek, sylhet, sundarbans, bali, maldives, kyoto, paris, interlaken, dubai,
                santorini, pokhara));
    }

    /** Short aliases so the seed data above stays readable. */
    private static final class Img {
        static final String BEACH = DataSeeder.BEACH, BEACH2 = DataSeeder.BEACH2, CLOUDS = DataSeeder.CLOUDS,
                HILLS = DataSeeder.HILLS, FOREST = DataSeeder.FOREST, BALI = DataSeeder.BALI,
                MALDIVES = DataSeeder.MALDIVES, KYOTO = DataSeeder.KYOTO, TOKYO = DataSeeder.TOKYO,
                PARIS = DataSeeder.PARIS, ALPS = DataSeeder.ALPS, DUBAI = DataSeeder.DUBAI,
                SANTORINI = DataSeeder.SANTORINI, NEPAL = DataSeeder.NEPAL, VENICE = DataSeeder.VENICE,
                LAKE = DataSeeder.LAKE, ROAD = DataSeeder.ROAD, DESERT = DataSeeder.DESERT, H_POOL = DataSeeder.H_POOL,
                H_LOBBY = DataSeeder.H_LOBBY, H_SUITE = DataSeeder.H_SUITE, RESORT = DataSeeder.RESORT;
    }

    // ------------------------------------------------------------------ hotels

    private void hotel(String id, String destId, String name, String address, int price, double rating, int reviews,
                       int stars, String description, List<String> images, List<String> amenities) {
        var h = new Hotel(id, destinations.getReferenceById(destId), name, address, price, rating, reviews, stars,
                description);
        h.getImageUrls().addAll(images);
        h.getAmenities().addAll(amenities);
        hotels.save(h);
    }

    private void seedHotels() {
        hotel("h-sayeman", "coxs-bazar", "Sea Pearl Beach Resort", "Marine Drive, Inani, Cox's Bazar", 9500, 4.7, 1840, 5,
                "Beachfront resort on Inani beach with a lagoon pool, water park and four restaurants.",
                List.of(RESORT, H_POOL, H_ROOM),
                List.of("Sea view", "Infinity pool", "Free Wi-Fi", "Spa", "Breakfast", "Airport shuttle"));
        hotel("h-ocean", "coxs-bazar", "Ocean Breeze Inn", "Kolatoli Road, Cox's Bazar", 3800, 4.3, 712, 3,
                "Friendly mid-range stay, a five-minute walk from Sugandha point.",
                List.of(H_ROOM, H_BED), List.of("Free Wi-Fi", "Breakfast", "Air conditioning", "Restaurant"));
        hotel("h-sajek", "sajek", "Cloud Nine Cottage", "Ruilui Para, Sajek", 4200, 4.6, 388, 3,
                "Wooden cottages on the ridge with balconies facing the cloud valley.",
                List.of(H_BED, CLOUDS), List.of("Valley view", "Balcony", "Bonfire", "Local meals"));
        hotel("h-sylhet", "sylhet", "Tea Valley Retreat", "Sreemangal, Moulvibazar", 7200, 4.8, 954, 5,
                "Eco-luxury retreat set inside a rainforest tea estate.",
                List.of(RESORT, HILLS), List.of("Pool", "Spa", "Tea garden walks", "Free Wi-Fi", "Breakfast"));
        hotel("h-bali", "bali", "Ubud Jungle Villas", "Jl. Raya Sayan, Ubud, Bali", 11500, 4.9, 2210, 5,
                "Private pool villas overlooking the Ayung river gorge.",
                List.of(H_POOL, BALI, H_SUITE), List.of("Private pool", "Jungle view", "Yoga deck", "Breakfast", "Free Wi-Fi"));
        hotel("h-maldives", "maldives", "Lagoon Overwater Resort", "South Malé Atoll, Maldives", 42000, 4.9, 1350, 5,
                "Glass-floor overwater villas with direct lagoon access.",
                List.of(MALDIVES, RESORT, H_SUITE),
                List.of("Overwater villa", "Snorkelling", "All inclusive", "Spa", "Seaplane transfer"));
        hotel("h-kyoto", "kyoto", "Gion Machiya Ryokan", "Higashiyama, Kyoto", 14500, 4.8, 860, 4,
                "Restored townhouse ryokan steps from Gion's lantern-lit lanes.",
                List.of(KYOTO, H_ROOM), List.of("Onsen bath", "Kaiseki dinner", "Tatami rooms", "Free Wi-Fi"));
        hotel("h-dubai", "dubai", "Marina Sky Tower Hotel", "Dubai Marina, Dubai", 15800, 4.7, 3120, 5,
                "Skyline suites with a 50th-floor infinity pool above the marina.",
                List.of(H_SUITE, DUBAI, H_POOL), List.of("Rooftop pool", "Gym", "Spa", "Free Wi-Fi", "Beach access"));
        hotel("h-pokhara", "pokhara", "Lakeside Himalaya Lodge", "Lakeside, Pokhara", 4600, 4.5, 640, 3,
                "Cosy lodge with rooftop views of Machapuchare and Phewa Lake.",
                List.of(LAKE, H_BED), List.of("Lake view", "Rooftop café", "Free Wi-Fi", "Trek desk"));
        hotel("h-paris", "paris", "Maison Saint-Germain", "6th arrondissement, Paris", 21000, 4.6, 1490, 4,
                "Boutique Left Bank hotel near Luxembourg Gardens.",
                List.of(H_LOBBY, PARIS, H_ROOM), List.of("Boutique design", "Breakfast", "Free Wi-Fi", "Concierge"));
    }

    // --------------------------------------------------------------- demo user

    private void seedDemoUser() {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) return;
        var user = users.save(new User("Demo Traveller", DEMO_EMAIL, encoder.encode(DEMO_PASSWORD)));

        LocalDate start = LocalDate.now().plusDays(21);
        var trip = new Trip(user, destinations.getReferenceById("coxs-bazar"), "Cox's Bazar Getaway", start,
                start.plusDays(3), 2, 40000);
        trip.syncDays();
        trip.day(1).addActivity(new Activity("Flight Dhaka → Cox's Bazar", "08:00", ActivityType.TRANSPORT, "", 9000));
        trip.day(1).addActivity(new Activity("Check in at Sea Pearl Resort", "12:00", ActivityType.STAY, "", 0));
        trip.day(1).addActivity(new Activity("Sunset at Laboni Beach", "17:30", ActivityType.SIGHTSEEING, "", 0));
        trip.day(2).addActivity(new Activity("Marine Drive to Himchari", "09:00", ActivityType.ADVENTURE, "", 2500));
        trip.day(2).addActivity(new Activity("Seafood lunch at Inani", "13:30", ActivityType.FOOD, "", 1800));
        trip.day(3).addActivity(new Activity("Burmese Market shopping", "11:00", ActivityType.SHOPPING, "", 3000));
        trip.day(4).addActivity(new Activity("Flight back to Dhaka", "15:00", ActivityType.TRANSPORT, "", 0));
        trips.save(trip);

        LocalDate today = LocalDate.now();
        expenses.saveAll(List.of(
                new Expense(user, trip, "Dhaka → Cox's Bazar flights", 9000, ExpenseCategory.FLIGHT, today.minusDays(2)),
                new Expense(user, trip, "Sea Pearl Resort deposit", 9500, ExpenseCategory.HOTEL, today.minusDays(3)),
                new Expense(user, trip, "Beach gear", 2200, ExpenseCategory.SHOPPING, today.minusDays(4)),
                new Expense(user, trip, "Airport transfer", 800, ExpenseCategory.TRANSPORT, today.minusDays(4))));
    }
}
