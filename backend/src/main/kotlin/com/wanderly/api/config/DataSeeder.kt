package com.wanderly.api.config

import com.wanderly.api.domain.*
import com.wanderly.api.domain.DestinationCategory.*
import com.wanderly.api.domain.Season.*
import com.wanderly.api.repository.*
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** Unsplash photography (each photo checked to match its place). */
private object Img {
    private fun u(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=1000&q=80"
    val BEACH = u("photo-1507525428034-b723cf961d3e")
    val BEACH2 = u("photo-1506929562872-bb421503ef21")
    val CLOUDS = u("photo-1464822759023-fed622ff2c3b")
    val HILLS = u("photo-1433086966358-54859d0ed716")
    val DESERT = u("photo-1500530855697-b586d89ba3ee")
    val FOREST = u("photo-1448375240586-882707db888b")
    val BALI = u("photo-1537996194471-e657df975ab4")
    val MALDIVES = u("photo-1514282401047-d79a71a590e8")
    val KYOTO = u("photo-1493976040374-85c8e12f0c0e")
    val TOKYO = u("photo-1540959733332-eab4deabeeaf")
    val PARIS = u("photo-1502602898657-3e91760cbb34")
    val ALPS = u("photo-1476514525535-07fb3b4ae5f1")
    val DUBAI = u("photo-1512453979798-5ea266f8880c")
    val SANTORINI = u("photo-1570077188670-e3a8d69ac5ff")
    val NEPAL = u("photo-1544735716-392fe2489ffa")
    val VENICE = u("photo-1523906834658-6e24ef2386f9")
    val LAKE = u("photo-1501785888041-af3ef285b470")
    val H_POOL = u("photo-1566073771259-6a8506099945")
    val H_LOBBY = u("photo-1551882547-ff40c63fe5fa")
    val RESORT = u("photo-1520250497591-112f2f40a3f4")
    val H_ROOM = u("photo-1542314831-068cd1dbfeeb")
    val H_SUITE = u("photo-1582719478250-c89cae4dc85b")
    val H_BED = u("photo-1571896349842-33c89424de2d")
}

/**
 * Loads the same catalogue as the Flutter app's MockData on first start (only when the
 * destinations table is empty), plus a demo account: demo@wanderly.app / wanderly123
 */
@Component
@Order(1)
class DataSeeder(
    private val props: WanderlyProperties,
    private val destinations: DestinationRepository,
    private val hotels: HotelRepository,
    private val users: UserRepository,
    private val trips: TripRepository,
    private val expenses: ExpenseRepository,
    private val encoder: PasswordEncoder,
) : ApplicationRunner {

    companion object {
        const val DEMO_EMAIL = "demo@wanderly.app"
        const val DEMO_PASSWORD = "wanderly123"
        private val log = LoggerFactory.getLogger(DataSeeder::class.java)
    }

    @Transactional
    override fun run(args: ApplicationArguments) {
        if (!props.seed.enabled || destinations.count() > 0) return
        seedDestinations()
        seedHotels()
        seedDemoUser()
        log.info("Seeded {} destinations, {} hotels and demo user {}", destinations.count(), hotels.count(), DEMO_EMAIL)
    }

    private class Seed(
        val id: String, val name: String, val country: String, val category: DestinationCategory, val tagline: String,
        val description: String, val image: String, val gallery: List<String>, val seasons: List<Season>,
        val bestTime: String, val rating: Double, val reviews: Int, val temp: Int, val trending: Boolean,
        val popular: Boolean, val cost: CostEstimate, val highlights: List<String>,
        val attractions: List<Attraction>, val seedReviews: List<Review>,
    )

    private fun review(author: String, rating: Double, comment: String, y: Int, m: Int, d: Int) =
        Review(author, rating, comment, LocalDate.of(y, m, d))

    private fun Seed.toEntity() = Destination(id, name, country, category).also { d ->
        d.tagline = tagline
        d.description = description
        d.imageUrl = image
        d.gallery += gallery
        d.bestSeasons += seasons
        d.bestTime = bestTime
        d.avgTempC = temp
        d.trending = trending
        d.popular = popular
        d.cost = cost
        d.highlights += highlights
        attractions.forEach(d::addAttraction)
        seedReviews.forEach(d::addReview)
        // Keep the curated rating / review count rather than the 1–2 seeded reviews.
        d.rating = rating
        d.reviewCount = reviews
    }

    private fun seedDestinations() {
        val seeds = listOf(
            Seed(
                "coxs-bazar", "Cox's Bazar", "Bangladesh", BEACH, "The world's longest natural sea beach",
                "Stretching 120 km along the Bay of Bengal, Cox's Bazar is an unbroken ribbon of golden sand, fishing " +
                    "villages and fiery sunsets. Ride the Marine Drive to Himchari waterfalls, hop to St. Martin's " +
                    "coral island, and end every evening with fresh seafood on Laboni Beach.",
                Img.BEACH, listOf(Img.BEACH, Img.BEACH2, Img.MALDIVES), listOf(WINTER, SPRING), "November – March",
                4.7, 2841, 26, true, true, CostEstimate(3500, 4500, 1200, 800),
                listOf("120 km sea beach", "Marine Drive road", "Fresh seafood", "St. Martin's day trip"),
                listOf(
                    Attraction("Laboni Beach", "Beach", Img.BEACH2, 4.6, 1.2),
                    Attraction("Himchari National Park", "Nature", Img.FOREST, 4.5, 12.0),
                    Attraction("Inani Beach", "Beach", Img.BEACH, 4.8, 26.0),
                ),
                listOf(
                    review("Nusrat J.", 5.0, "Sunsets on Marine Drive are unreal. Go in December!", 2026, 1, 12),
                    review("Tanvir A.", 4.5, "Great food and very affordable. Inani is calmer than Laboni.", 2025, 12, 3),
                ),
            ),
            Seed(
                "sajek", "Sajek Valley", "Bangladesh", MOUNTAIN, "Wake up above a sea of clouds",
                "Perched on the hills of Rangamati, Sajek is famous for dawn views over rolling clouds, colourful " +
                    "indigenous villages and cosy wooden cottages. A jeep ride from Khagrachari winds through jungle " +
                    "and tea gardens before reaching Konglak Para, the highest point.",
                Img.CLOUDS, listOf(Img.CLOUDS, Img.HILLS, Img.NEPAL), listOf(MONSOON, AUTUMN), "June – November",
                4.8, 1920, 22, true, false, CostEstimate(2800, 3500, 900, 600),
                listOf("Sunrise above clouds", "Konglak Para hike", "Indigenous culture", "Bamboo chicken"),
                listOf(
                    Attraction("Konglak Para", "Viewpoint", Img.CLOUDS, 4.9, 2.0),
                    Attraction("Ruilui Para", "Village", Img.HILLS, 4.4, 0.5),
                    Attraction("Hazachora Waterfall", "Waterfall", Img.FOREST, 4.5, 18.0),
                ),
                listOf(
                    review("Farhan R.", 5.0, "The cloud view at 6am is worth every bumpy jeep minute.", 2025, 9, 18),
                    review("Mim S.", 4.5, "Book cottages early during holidays.", 2025, 8, 2),
                ),
            ),
            Seed(
                "sylhet", "Sylhet", "Bangladesh", ADVENTURE, "Tea gardens, swamp forests and crystal rivers",
                "Endless emerald tea estates roll across Sreemangal and Sylhet. Paddle through Ratargul, the only " +
                    "freshwater swamp forest in the country, then cool off in the crystal stones of Bholaganj and " +
                    "Jaflong right on the Meghalaya border.",
                Img.HILLS, listOf(Img.HILLS, Img.FOREST, Img.LAKE), listOf(MONSOON, AUTUMN), "June – October",
                4.6, 1504, 25, false, true, CostEstimate(1500, 3000, 900, 700),
                listOf("Ratargul swamp boat ride", "Seven-layer tea", "Jaflong stones", "Lalakhal blue river"),
                listOf(
                    Attraction("Ratargul Swamp Forest", "Nature", Img.FOREST, 4.7, 26.0),
                    Attraction("Jaflong", "River", Img.LAKE, 4.5, 60.0),
                    Attraction("Lakkatura Tea Estate", "Tea garden", Img.HILLS, 4.6, 4.0),
                ),
                listOf(review("Arif H.", 4.5, "Ratargul in monsoon is magical.", 2025, 7, 22)),
            ),
            Seed(
                "sundarbans", "Sundarbans", "Bangladesh", ADVENTURE, "Home of the Royal Bengal Tiger",
                "The largest mangrove forest on Earth is a maze of tidal rivers and islands. Multi-day boat safaris glide " +
                    "past spotted deer, crocodiles and kingfishers, with a lucky few spotting the elusive Royal " +
                    "Bengal Tiger at Kotka or Kochikhali.",
                Img.FOREST, listOf(Img.FOREST, Img.LAKE, Img.HILLS), listOf(WINTER), "November – February",
                4.7, 980, 24, false, false, CostEstimate(2500, 5500, 800, 1500),
                listOf("3-day boat safari", "UNESCO World Heritage", "Wildlife watching", "Karamjal centre"),
                listOf(
                    Attraction("Kotka Beach", "Wildlife", Img.FOREST, 4.6, 0.0),
                    Attraction("Karamjal", "Eco park", Img.LAKE, 4.3, 0.0),
                ),
                listOf(review("Sadia K.", 5.0, "Saw deer and a crocodile! Choose a licensed tour operator.", 2026, 1, 28)),
            ),
            Seed(
                "bali", "Bali", "Indonesia", BEACH, "Island of the Gods",
                "Terraced rice fields, cliff-top temples and world-class surf. Base yourself in Ubud for jungle, yoga and " +
                    "culture, then head to Uluwatu and Canggu for beaches and sunset beach clubs.",
                Img.BALI, listOf(Img.BALI, Img.BEACH, Img.RESORT), listOf(SPRING, SUMMER), "April – October",
                4.8, 12840, 28, true, true, CostEstimate(48000, 7500, 2500, 3000),
                listOf("Tegallalang rice terraces", "Uluwatu temple", "Surfing", "Balinese cuisine"),
                listOf(
                    Attraction("Tegallalang Rice Terrace", "Nature", Img.BALI, 4.7, 10.0),
                    Attraction("Uluwatu Temple", "Temple", Img.BEACH2, 4.8, 45.0),
                    Attraction("Sacred Monkey Forest", "Nature", Img.FOREST, 4.5, 2.0),
                ),
                listOf(
                    review("Emma L.", 5.0, "Ubud stole my heart. Rent a scooter!", 2026, 5, 10),
                    review("Rahim U.", 4.5, "Great value — villas with pools for hostel prices.", 2026, 3, 4),
                ),
            ),
            Seed(
                "maldives", "Maldives", "Maldives", BEACH, "Overwater villas and turquoise lagoons",
                "1,192 coral islands floating in the Indian Ocean. Snorkel with manta rays, sleep above the lagoon, or " +
                    "stay on a local island like Maafushi for a budget-friendly paradise.",
                Img.MALDIVES, listOf(Img.MALDIVES, Img.BEACH, Img.RESORT), listOf(WINTER, SPRING), "December – April",
                4.9, 8410, 29, false, true, CostEstimate(42000, 18000, 4000, 6000),
                listOf("Overwater villas", "Manta ray snorkelling", "Sandbank picnics", "Bioluminescent beach"),
                listOf(
                    Attraction("Maafushi Island", "Island", Img.BEACH2, 4.6, 0.0),
                    Attraction("Banana Reef", "Dive site", Img.MALDIVES, 4.8, 0.0),
                ),
                listOf(review("Sara M.", 5.0, "Clearest water I have ever seen.", 2026, 2, 14)),
            ),
            Seed(
                "kyoto", "Kyoto", "Japan", CULTURE, "Temples, tea houses and cherry blossoms",
                "Japan's ancient capital is home to 1,600 temples, geisha districts and zen gardens. Walk the vermilion " +
                    "torii gates of Fushimi Inari at dawn and get lost in the bamboo groves of Arashiyama.",
                Img.KYOTO, listOf(Img.KYOTO, Img.TOKYO, Img.FOREST), listOf(SPRING, AUTUMN), "March – May, Oct – Nov",
                4.9, 15230, 16, true, false, CostEstimate(85000, 11000, 4500, 2500),
                listOf("Fushimi Inari", "Arashiyama bamboo", "Gion district", "Kaiseki dining"),
                listOf(
                    Attraction("Fushimi Inari Taisha", "Shrine", Img.KYOTO, 4.9, 5.0),
                    Attraction("Arashiyama Bamboo Grove", "Nature", Img.FOREST, 4.6, 9.0),
                    Attraction("Kinkaku-ji", "Temple", Img.LAKE, 4.8, 7.0),
                ),
                listOf(review("Kenji T.", 5.0, "Go to Fushimi Inari before 7am for empty paths.", 2026, 4, 2)),
            ),
            Seed(
                "paris", "Paris", "France", CITY, "The city of light",
                "Boulevards, bistros and the world's greatest museums. Picnic under the Eiffel Tower, browse Montmartre's " +
                    "artists and cruise the Seine at golden hour.",
                Img.PARIS, listOf(Img.PARIS, Img.VENICE, Img.H_LOBBY), listOf(SPRING, AUTUMN), "April – June, Sep – Oct",
                4.7, 22100, 15, false, true, CostEstimate(95000, 16000, 6000, 4000),
                listOf("Eiffel Tower", "Louvre Museum", "Seine river cruise", "Montmartre"),
                listOf(
                    Attraction("Eiffel Tower", "Landmark", Img.PARIS, 4.8, 3.0),
                    Attraction("Louvre Museum", "Museum", Img.H_LOBBY, 4.8, 2.0),
                ),
                listOf(review("Chloé D.", 4.5, "Buy museum tickets online to skip the queues.", 2026, 6, 20)),
            ),
            Seed(
                "interlaken", "Interlaken", "Switzerland", MOUNTAIN, "Adventure capital of the Alps",
                "Sandwiched between two glacial lakes beneath the Eiger, Mönch and Jungfrau. Paraglide over emerald water, " +
                    "ride cogwheel trains to the Top of Europe and hike flower-filled valleys.",
                Img.ALPS, listOf(Img.ALPS, Img.LAKE, Img.CLOUDS), listOf(SUMMER, WINTER), "June – September",
                4.9, 6720, 12, false, false, CostEstimate(105000, 20000, 7000, 9000),
                listOf("Jungfraujoch", "Paragliding", "Lake Brienz", "Lauterbrunnen valley"),
                listOf(
                    Attraction("Jungfraujoch", "Mountain", Img.CLOUDS, 4.8, 25.0),
                    Attraction("Lake Brienz", "Lake", Img.LAKE, 4.9, 3.0),
                ),
                listOf(review("Lukas B.", 5.0, "Get the Berner Oberland pass — it saves a lot.", 2025, 8, 9)),
            ),
            Seed(
                "dubai", "Dubai", "UAE", CITY, "Skylines, souks and desert dunes",
                "Futuristic towers rise beside traditional souks. Watch the city from the Burj Khalifa, bash dunes on a " +
                    "desert safari and shop until midnight.",
                Img.DUBAI, listOf(Img.DUBAI, Img.H_POOL, Img.H_SUITE), listOf(WINTER), "November – March",
                4.6, 18300, 30, true, false, CostEstimate(45000, 12000, 4000, 5000),
                listOf("Burj Khalifa", "Desert safari", "Gold souk", "Dubai Marina"),
                listOf(
                    Attraction("Burj Khalifa", "Landmark", Img.DUBAI, 4.7, 1.0),
                    Attraction("Desert Safari", "Adventure", Img.DESERT, 4.6, 45.0),
                ),
                listOf(review("Omar F.", 4.5, "Desert safari was the highlight.", 2026, 1, 3)),
            ),
            Seed(
                "santorini", "Santorini", "Greece", BEACH, "Whitewashed cliffs above the Aegean",
                "Blue-domed churches cling to volcanic cliffs above a sunken caldera. Watch the famous Oia sunset, swim at " +
                    "red and black sand beaches and taste wines grown in volcanic soil.",
                Img.SANTORINI, listOf(Img.SANTORINI, Img.BEACH2, Img.H_POOL), listOf(SPRING, SUMMER), "May – October",
                4.8, 9900, 24, false, false, CostEstimate(98000, 17000, 5500, 4000),
                listOf("Oia sunset", "Caldera cruise", "Red Beach", "Wine tasting"),
                listOf(
                    Attraction("Oia Village", "Village", Img.SANTORINI, 4.9, 0.0),
                    Attraction("Red Beach", "Beach", Img.BEACH2, 4.4, 0.0),
                ),
                listOf(review("Anna P.", 5.0, "Stay in Imerovigli for the views without the crowds.", 2025, 9, 1)),
            ),
            Seed(
                "pokhara", "Pokhara", "Nepal", MOUNTAIN, "Gateway to the Annapurnas",
                "A laid-back lakeside town with the Annapurna range mirrored on Phewa Lake. Paraglide from Sarangkot, trek " +
                    "to Poon Hill, and row out to the Tal Barahi temple.",
                Img.NEPAL, listOf(Img.NEPAL, Img.CLOUDS, Img.LAKE), listOf(AUTUMN, SPRING), "October – April",
                4.7, 4300, 19, false, true, CostEstimate(22000, 4000, 1500, 2500),
                listOf("Sarangkot sunrise", "Phewa Lake", "Paragliding", "Poon Hill trek"),
                listOf(
                    Attraction("Sarangkot", "Viewpoint", Img.NEPAL, 4.8, 8.0),
                    Attraction("Phewa Lake", "Lake", Img.LAKE, 4.7, 1.0),
                ),
                listOf(review("Imran K.", 4.5, "Perfect first trip abroad from Dhaka — affordable and beautiful.", 2025, 11, 11)),
            ),
        )
        destinations.saveAll(seeds.map { it.toEntity() })
    }

    private fun hotel(
        id: String, destId: String, name: String, address: String, price: Int, rating: Double, reviews: Int,
        stars: Int, description: String, images: List<String>, amenities: List<String>,
    ) {
        val h = Hotel(id, destinations.getReferenceById(destId), name, address, price, rating, reviews, stars, description)
        h.imageUrls += images
        h.amenities += amenities
        hotels.save(h)
    }

    private fun seedHotels() {
        hotel("h-sayeman", "coxs-bazar", "Sea Pearl Beach Resort", "Marine Drive, Inani, Cox's Bazar", 9500, 4.7, 1840, 5,
            "Beachfront resort on Inani beach with a lagoon pool, water park and four restaurants.",
            listOf(Img.RESORT, Img.H_POOL, Img.H_ROOM), listOf("Sea view", "Infinity pool", "Free Wi-Fi", "Spa", "Breakfast", "Airport shuttle"))
        hotel("h-ocean", "coxs-bazar", "Ocean Breeze Inn", "Kolatoli Road, Cox's Bazar", 3800, 4.3, 712, 3,
            "Friendly mid-range stay, a five-minute walk from Sugandha point.",
            listOf(Img.H_ROOM, Img.H_BED), listOf("Free Wi-Fi", "Breakfast", "Air conditioning", "Restaurant"))
        hotel("h-sajek", "sajek", "Cloud Nine Cottage", "Ruilui Para, Sajek", 4200, 4.6, 388, 3,
            "Wooden cottages on the ridge with balconies facing the cloud valley.",
            listOf(Img.H_BED, Img.CLOUDS), listOf("Valley view", "Balcony", "Bonfire", "Local meals"))
        hotel("h-sylhet", "sylhet", "Tea Valley Retreat", "Sreemangal, Moulvibazar", 7200, 4.8, 954, 5,
            "Eco-luxury retreat set inside a rainforest tea estate.",
            listOf(Img.RESORT, Img.HILLS), listOf("Pool", "Spa", "Tea garden walks", "Free Wi-Fi", "Breakfast"))
        hotel("h-bali", "bali", "Ubud Jungle Villas", "Jl. Raya Sayan, Ubud, Bali", 11500, 4.9, 2210, 5,
            "Private pool villas overlooking the Ayung river gorge.",
            listOf(Img.H_POOL, Img.BALI, Img.H_SUITE), listOf("Private pool", "Jungle view", "Yoga deck", "Breakfast", "Free Wi-Fi"))
        hotel("h-maldives", "maldives", "Lagoon Overwater Resort", "South Malé Atoll, Maldives", 42000, 4.9, 1350, 5,
            "Glass-floor overwater villas with direct lagoon access.",
            listOf(Img.MALDIVES, Img.RESORT, Img.H_SUITE), listOf("Overwater villa", "Snorkelling", "All inclusive", "Spa", "Seaplane transfer"))
        hotel("h-kyoto", "kyoto", "Gion Machiya Ryokan", "Higashiyama, Kyoto", 14500, 4.8, 860, 4,
            "Restored townhouse ryokan steps from Gion's lantern-lit lanes.",
            listOf(Img.KYOTO, Img.H_ROOM), listOf("Onsen bath", "Kaiseki dinner", "Tatami rooms", "Free Wi-Fi"))
        hotel("h-dubai", "dubai", "Marina Sky Tower Hotel", "Dubai Marina, Dubai", 15800, 4.7, 3120, 5,
            "Skyline suites with a 50th-floor infinity pool above the marina.",
            listOf(Img.H_SUITE, Img.DUBAI, Img.H_POOL), listOf("Rooftop pool", "Gym", "Spa", "Free Wi-Fi", "Beach access"))
        hotel("h-pokhara", "pokhara", "Lakeside Himalaya Lodge", "Lakeside, Pokhara", 4600, 4.5, 640, 3,
            "Cosy lodge with rooftop views of Machapuchare and Phewa Lake.",
            listOf(Img.LAKE, Img.H_BED), listOf("Lake view", "Rooftop café", "Free Wi-Fi", "Trek desk"))
        hotel("h-paris", "paris", "Maison Saint-Germain", "6th arrondissement, Paris", 21000, 4.6, 1490, 4,
            "Boutique Left Bank hotel near Luxembourg Gardens.",
            listOf(Img.H_LOBBY, Img.PARIS, Img.H_ROOM), listOf("Boutique design", "Breakfast", "Free Wi-Fi", "Concierge"))
    }

    private fun seedDemoUser() {
        if (users.existsByEmailIgnoreCase(DEMO_EMAIL)) return
        val user = users.save(User("Demo Traveller", DEMO_EMAIL, encoder.encode(DEMO_PASSWORD)!!))

        val start = LocalDate.now().plusDays(21)
        val trip = Trip(user, destinations.getReferenceById("coxs-bazar"), "Cox's Bazar Getaway", start, start.plusDays(3), 2, 40_000)
            .apply { syncDays() }
        fun add(day: Int, title: String, time: String, type: ActivityType, cost: Int = 0) =
            trip.day(day)!!.addActivity(Activity(title, time, type, "", cost))
        add(1, "Flight Dhaka → Cox's Bazar", "08:00", ActivityType.TRANSPORT, 9000)
        add(1, "Check in at Sea Pearl Resort", "12:00", ActivityType.STAY)
        add(1, "Sunset at Laboni Beach", "17:30", ActivityType.SIGHTSEEING)
        add(2, "Marine Drive to Himchari", "09:00", ActivityType.ADVENTURE, 2500)
        add(2, "Seafood lunch at Inani", "13:30", ActivityType.FOOD, 1800)
        add(3, "Burmese Market shopping", "11:00", ActivityType.SHOPPING, 3000)
        add(4, "Flight back to Dhaka", "15:00", ActivityType.TRANSPORT)
        trips.save(trip)

        val today = LocalDate.now()
        expenses.saveAll(
            listOf(
                Expense(user, trip, "Dhaka → Cox's Bazar flights", 9000, ExpenseCategory.FLIGHT, today.minusDays(2)),
                Expense(user, trip, "Sea Pearl Resort deposit", 9500, ExpenseCategory.HOTEL, today.minusDays(3)),
                Expense(user, trip, "Beach gear", 2200, ExpenseCategory.SHOPPING, today.minusDays(4)),
                Expense(user, trip, "Airport transfer", 800, ExpenseCategory.TRANSPORT, today.minusDays(4)),
            )
        )
    }
}
