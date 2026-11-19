package com.wanderly.api.service

import com.wanderly.api.domain.ActivityType
import com.wanderly.api.domain.Destination
import com.wanderly.api.domain.DestinationCategory
import com.wanderly.api.domain.Season
import com.wanderly.api.dto.*
import com.wanderly.api.exception.ApiException
import com.wanderly.api.repository.DestinationRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Currency converter and smart packing list. */
@Service
class ToolsService(private val destinations: DestinationService) {

    companion object {
        /** 1 unit of currency = x BDT. Static demo rates — replace with a live exchange-rate API (cached). */
        val BDT_RATES: Map<String, Double> = linkedMapOf(
            "BDT" to 1.0, "USD" to 122.0, "EUR" to 132.5, "GBP" to 155.0, "CAD" to 88.5, "AUD" to 80.0,
            "INR" to 1.45, "JPY" to 0.81, "AED" to 33.2, "SGD" to 91.0, "MYR" to 27.5,
        )
    }

    fun convert(from: String, to: String, amount: Double): ConversionResult {
        val f = BDT_RATES[from.uppercase()]
        val t = BDT_RATES[to.uppercase()]
        if (f == null || t == null) throw ApiException.badRequest("Supported currencies: ${BDT_RATES.keys}")
        val rate = f / t
        return ConversionResult(
            from.uppercase(), to.uppercase(), amount,
            (amount * rate * 100).roundToLong() / 100.0, (rate * 1_000_000).roundToLong() / 1_000_000.0,
        )
    }

    @Transactional(readOnly = true)
    fun packing(destinationId: String, days: Int): PackingList {
        if (days !in 1..60) throw ApiException.badRequest("days must be between 1 and 60")
        val d = destinations.find(destinationId)
        val outfits = max(2, min(10, ceil(days * 0.8).toInt()))
        val hot = d.avgTempC >= 25
        val cold = d.avgTempC <= 16
        val abroad = !d.country.equals("Bangladesh", ignoreCase = true)
        val rainy = Season.MONSOON in d.bestSeasons
        val cat = d.category

        val documents = buildList {
            if (abroad) addAll(listOf("Passport (6+ months validity)", "Visa / e-visa printout", "Travel insurance"))
            else add("National ID card")
            addAll(listOf("Booking confirmations", "Emergency contacts"))
        }
        val clothing = buildList {
            addAll(listOf("$outfits × tops", "${ceil(outfits / 2.0).toInt()} × bottoms", "$days × underwear & socks", "Sleepwear"))
            if (hot) addAll(listOf("Light cotton shirts", "Sun hat", "Sunglasses"))
            if (cold) addAll(listOf("Warm jacket", "Thermal layer", "Beanie & gloves"))
            if (rainy) add("Rain jacket / poncho")
            if (cat == DestinationCategory.BEACH) addAll(listOf("Swimwear", "Flip-flops", "Beach towel"))
            if (cat == DestinationCategory.MOUNTAIN || cat == DestinationCategory.ADVENTURE) addAll(listOf("Hiking shoes", "Quick-dry trousers"))
            if (cat == DestinationCategory.CITY || cat == DestinationCategory.CULTURE) addAll(listOf("Comfortable walking shoes", "One smart outfit"))
        }
        val toiletries = buildList {
            addAll(listOf("Toothbrush & toothpaste", "Shampoo & soap"))
            if (hot || cat == DestinationCategory.BEACH) add("Sunscreen SPF 50")
            addAll(listOf("Insect repellent", "Personal medication", "Basic first-aid kit"))
        }
        val electronics = buildList {
            addAll(listOf("Phone & charger", "Power bank"))
            if (abroad) add("Universal travel adapter")
            if (cat == DestinationCategory.BEACH) add("Waterproof phone pouch")
            add("Earphones")
        }
        val extras = listOf(
            "Reusable water bottle", "Day backpack",
            if (abroad) "Some local cash (${d.country})" else "Cash for local markets", "Snacks for the journey",
        )
        return PackingList(
            d.id, days, listOf(
                PackingSection("Documents", documents), PackingSection("Clothing", clothing),
                PackingSection("Toiletries", toiletries), PackingSection("Electronics", electronics),
                PackingSection("Extras", extras),
            )
        )
    }
}

/**
 * Rule-based trip planner. Parses destination, length, travellers and budget from free
 * text and builds an itinerary + cost estimate. The response shape is designed so an
 * LLM-backed implementation can replace this class without changing the API.
 */
@Service
class AiPlannerService(private val destinations: DestinationRepository) {

    data class PlanRequest(val prompt: String? = null)

    data class Plan(
        val destination: DestinationSummary, val days: Int, val travelers: Int, val requestedBudget: Int?,
        val estimate: Int, val fitsBudget: Boolean, val breakdown: Map<String, Int>,
        val itinerary: List<DayRequest>, val tips: List<String>,
    )

    data class PlanResponse(val reply: String, val plan: Plan?, val suggestions: List<DestinationSummary>)

    companion object {
        private val ALIASES = mapOf(
            "coxs" to "coxs-bazar", "cox" to "coxs-bazar", "sreemangal" to "sylhet", "nepal" to "pokhara",
            "japan" to "kyoto", "switzerland" to "interlaken", "greece" to "santorini",
        )
        private val DAYS = Regex("(\\d+)\\s*[- ]?\\s*(days?|nights?)")
        private val TRAVELERS = Regex("(\\d+)\\s*(people|persons|travell?ers|friends|adults|of us)")
        private val BUDGET_PREFIX = Regex("(?:৳|tk\\.?|bdt|taka|under|budget|within|below)\\s*([\\d,]+(?:\\.\\d+)?)\\s*(k|lakh|lac)?")
        private val BUDGET_SUFFIX = Regex("([\\d,]+(?:\\.\\d+)?)\\s*(k|lakh|lac)?\\s*(?:৳|tk|bdt|taka)")

        fun match(p: String, all: List<Destination>): Destination? {
            all.firstOrNull { d ->
                val name = d.name.lowercase()
                p.contains(name) || p.contains(name.replace("'", "").replace("’", "")) || p.contains(d.id.replace('-', ' '))
            }?.let { return it }
            return ALIASES.entries.firstOrNull { p.contains(it.key) }?.let { e -> all.firstOrNull { it.id == e.value } }
        }

        fun parseDays(p: String): Int? {
            DAYS.find(p)?.let { m ->
                val n = m.groupValues[1].toInt()
                return if (m.groupValues[2].startsWith("night")) n + 1 else n
            }
            return when {
                p.contains("weekend") -> 2
                p.contains("week") -> 7
                else -> null
            }
        }

        fun parseTravelers(p: String): Int? {
            TRAVELERS.find(p)?.let { return it.groupValues[1].toInt() }
            return when {
                p.contains("couple") || p.contains("honeymoon") -> 2
                p.contains("family") -> 4
                else -> null
            }
        }

        fun parseBudget(p: String): Int? {
            val m = BUDGET_PREFIX.find(p) ?: BUDGET_SUFFIX.find(p) ?: return null
            var value = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
            when (m.groupValues[2]) {
                "k" -> value *= 1_000
                "lakh", "lac" -> value *= 100_000
            }
            return if (value < 500) null else value.roundToInt()
        }

        private fun fmt(v: Int) = NumberFormat.getIntegerInstance(Locale.US).format(v)
    }

    @Transactional(readOnly = true)
    fun plan(prompt: String): PlanResponse {
        val p = prompt.lowercase()
        val all = destinations.findAll()
        val dest = match(p, all)
            ?: return suggest(p, all).let { ideas ->
                PlanResponse(
                    "I couldn't spot a destination in that. Try something like " +
                        "\"Plan a 4-day trip to Cox's Bazar under ৳20,000\". Some ideas: ${ideas.joinToString { it.name }}.",
                    null, ideas,
                )
            }

        val days = (parseDays(p) ?: 3).coerceIn(1, 14)
        val travelers = (parseTravelers(p) ?: 1).coerceIn(1, 20)
        val budget = parseBudget(p)
        val plan = build(dest, days, travelers, budget)

        val reply = buildString {
            append("Here is a $days-day plan for ${dest.name}")
            if (travelers > 1) append(" for $travelers travellers")
            append(". Estimated cost is ৳${fmt(plan.estimate)}")
            when {
                budget == null -> append('.')
                plan.fitsBudget -> append(" — comfortably within your ৳${fmt(budget)} budget. 🎉")
                else -> append(
                    ". That is ৳${fmt(plan.estimate - budget)} over your ৳${fmt(budget)} budget, so I picked budget " +
                        "stays — you could also trim a day or travel by bus."
                )
            }
        }
        return PlanResponse(reply, plan, emptyList())
    }

    private fun suggest(p: String, all: List<Destination>): List<DestinationSummary> {
        DestinationCategory.entries.firstOrNull { p.contains(it.json) }?.let { c ->
            return all.filter { it.category == c }.take(3).map(DestinationSummary::from)
        }
        return all.sortedByDescending { it.rating }.take(3).map(DestinationSummary::from)
    }

    private fun build(d: Destination, days: Int, travelers: Int, budget: Int?): Plan {
        val c = d.cost
        val nights = max(days - 1, 1)
        val rooms = ceil(travelers / 2.0).toInt()
        val fixed = c.transport * travelers + (c.foodPerDay + c.activitiesPerDay) * days * travelers
        // Downgrade the stay when the plan would be over budget.
        val hotelRate = if (budget != null && fixed + c.hotelPerNight * nights * rooms > budget)
            (c.hotelPerNight * 0.55).roundToInt() else c.hotelPerNight

        val breakdown = linkedMapOf(
            "Transport" to c.transport * travelers,
            "Stay" to hotelRate * nights * rooms,
            "Food" to c.foodPerDay * days * travelers,
            "Activities" to c.activitiesPerDay * days * travelers,
        )
        val estimate = breakdown.values.sum()
        val attractions = d.attractions

        val itinerary = (0 until days).map { i ->
            val last = i == days - 1
            val acts = mutableListOf(
                if (i == 0) ActivityRequest("Arrive in ${d.name} & check in", "11:00", ActivityType.STAY, "", 0)
                else ActivityRequest("Breakfast near your stay", "08:30", ActivityType.FOOD, "", 0)
            )
            if (attractions.isNotEmpty()) {
                val a = attractions[i % attractions.size]
                val adventure = a.type?.lowercase()?.contains("adventure") == true
                acts += ActivityRequest(
                    if (i < attractions.size) "Explore ${a.name}" else "Revisit ${a.name} at golden hour",
                    if (i == 0) "15:00" else "10:00",
                    if (adventure) ActivityType.ADVENTURE else ActivityType.SIGHTSEEING,
                    a.type, (c.activitiesPerDay * 0.6).roundToInt(),
                )
            }
            if (i > 0 && !last && i - 1 < d.highlights.size) {
                acts += ActivityRequest(d.highlights[i - 1], "14:00", ActivityType.ADVENTURE, "", 0)
            }
            acts += ActivityRequest(
                if (last) "Souvenir shopping & departure" else "Dinner — try local specialities",
                if (last) "16:00" else "19:30",
                if (last) ActivityType.SHOPPING else ActivityType.FOOD, "", c.foodPerDay / 2,
            )
            DayRequest(i + 1, acts)
        }

        val tips = buildList {
            add("Best time to visit: ${d.bestTime}.")
            if (d.avgTempC >= 26) add("Pack light cotton, sunscreen and sunglasses — it averages ${d.avgTempC}°C.")
            if (d.avgTempC < 18) add("Bring warm layers — average temperature is ${d.avgTempC}°C.")
            if (budget != null && estimate > budget) add("Travel mid-week and book stays 3+ weeks ahead to save 15–25%.")
            if (travelers > 2) add("Split a private car between $travelers people — often cheaper than individual tickets.")
        }
        return Plan(DestinationSummary.from(d), days, travelers, budget, estimate, budget == null || estimate <= budget, breakdown, itinerary, tips)
    }
}
