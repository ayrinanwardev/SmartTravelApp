package com.wanderly.api.config

import com.wanderly.api.repository.DestinationRepository
import com.wanderly.api.repository.HotelRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/** Approximate WGS84 coordinates for the seeded catalogue, used by the map screen. */
object GeoData {
    val DESTINATIONS = mapOf(
        "coxs-bazar" to (21.4272 to 92.0058), "sajek" to (23.3820 to 92.2938), "sylhet" to (24.8949 to 91.8687),
        "sundarbans" to (21.9497 to 89.1833), "bali" to (-8.5069 to 115.2625), "maldives" to (4.1755 to 73.5093),
        "kyoto" to (35.0116 to 135.7681), "paris" to (48.8566 to 2.3522), "interlaken" to (46.6863 to 7.8632),
        "dubai" to (25.2048 to 55.2708), "santorini" to (36.3932 to 25.4615), "pokhara" to (28.2096 to 83.9856),
    )

    /** Keyed by attraction name (unique across the seed data). */
    val ATTRACTIONS = mapOf(
        "Laboni Beach" to (21.4326 to 91.9744), "Himchari National Park" to (21.3634 to 92.0230),
        "Inani Beach" to (21.2297 to 92.0453), "Konglak Para" to (23.4103 to 92.3029),
        "Ruilui Para" to (23.3809 to 92.2934), "Hazachora Waterfall" to (23.1705 to 92.0450),
        "Ratargul Swamp Forest" to (25.0063 to 91.9250), "Jaflong" to (25.1640 to 92.0176),
        "Lakkatura Tea Estate" to (24.9246 to 91.8749), "Kotka Beach" to (21.8478 to 89.7740),
        "Karamjal" to (22.4228 to 89.5945), "Tegallalang Rice Terrace" to (-8.4314 to 115.2790),
        "Uluwatu Temple" to (-8.8291 to 115.0849), "Sacred Monkey Forest" to (-8.5188 to 115.2587),
        "Maafushi Island" to (3.9432 to 73.4906), "Banana Reef" to (4.2347 to 73.5310),
        "Fushimi Inari Taisha" to (34.9671 to 135.7727), "Arashiyama Bamboo Grove" to (35.0170 to 135.6713),
        "Kinkaku-ji" to (35.0394 to 135.7292), "Eiffel Tower" to (48.8584 to 2.2945),
        "Louvre Museum" to (48.8606 to 2.3376), "Jungfraujoch" to (46.5475 to 7.9853),
        "Lake Brienz" to (46.7270 to 7.9670), "Burj Khalifa" to (25.1972 to 55.2744),
        "Desert Safari" to (24.9900 to 55.6200), "Oia Village" to (36.4618 to 25.3753),
        "Red Beach" to (36.3486 to 25.3945), "Sarangkot" to (28.2440 to 83.9488), "Phewa Lake" to (28.2153 to 83.9450),
    )

    val HOTELS = mapOf(
        "h-sayeman" to (21.3833 to 92.0003), "h-ocean" to (21.4239 to 91.9800), "h-sajek" to (23.3815 to 92.2936),
        "h-sylhet" to (24.3065 to 91.7296), "h-bali" to (-8.5030 to 115.2440), "h-maldives" to (3.9000 to 73.4600),
        "h-kyoto" to (35.0037 to 135.7788), "h-dubai" to (25.0805 to 55.1403), "h-pokhara" to (28.2096 to 83.9590),
        "h-paris" to (48.8530 to 2.3330),
    )
}

/**
 * Fills in map coordinates for catalogue rows that don't have them yet (idempotent).
 * Runs after the seeder, so existing databases (e.g. Supabase) get coordinates without a migration.
 */
@Component
@Order(2)
class GeoBackfill(private val destinations: DestinationRepository, private val hotels: HotelRepository) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(GeoBackfill::class.java)

    @Transactional
    override fun run(args: ApplicationArguments) {
        var updated = 0
        for (d in destinations.findAll()) {
            GeoData.DESTINATIONS[d.id]?.takeIf { d.latitude == null }?.let { (lat, lng) ->
                d.latitude = lat; d.longitude = lng; updated++
            }
            for (a in d.attractions) {
                GeoData.ATTRACTIONS[a.name]?.takeIf { a.latitude == null }?.let { (lat, lng) ->
                    a.latitude = lat; a.longitude = lng; updated++
                }
            }
        }
        for (h in hotels.findAll()) {
            GeoData.HOTELS[h.id]?.takeIf { h.latitude == null }?.let { (lat, lng) ->
                h.latitude = lat; h.longitude = lng; updated++
            }
        }
        if (updated > 0) log.info("Added map coordinates to {} rows", updated)
    }
}
