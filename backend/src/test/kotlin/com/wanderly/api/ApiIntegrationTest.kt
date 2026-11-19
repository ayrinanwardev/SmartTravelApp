package com.wanderly.api

import com.jayway.jsonpath.JsonPath
import org.hamcrest.Matchers.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import java.time.LocalDate
import java.util.UUID

/** Full-stack tests: real Spring context, H2 database, JWT security, seeded data, fake payment gateway. */
@SpringBootTest
@ActiveProfiles("dev")
@Import(TestPaymentConfig::class)
@TestPropertySource(properties = ["wanderly.uploads.dir=target/test-uploads", "spring.datasource.url=jdbc:h2:mem:api-test;DB_CLOSE_DELAY=-1;MODE=PostgreSQL"])
class ApiIntegrationTest {

    @Autowired lateinit var context: WebApplicationContext
    @Autowired lateinit var gateway: FakePaymentGateway

    lateinit var mvc: MockMvc

    @BeforeEach
    fun setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply<org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder>(springSecurity()).build()
    }

    // ---------------------------------------------------------------- helpers

    private fun send(method: String, url: String, token: String? = null, json: String? = null): ResultActions {
        val req = request(org.springframework.http.HttpMethod.valueOf(method), url)
        if (json != null) req.contentType(MediaType.APPLICATION_JSON).content(json)
        if (token != null) req.header("Authorization", "Bearer $token")
        return mvc.perform(req)
    }

    private fun ResultActions.body(): String = andReturn().response.contentAsString
    private fun <T> String.at(path: String): T = JsonPath.read(this, path)

    private fun register(prefix: String): String {
        val email = "$prefix-${UUID.randomUUID().toString().take(8)}@example.com"
        return send("POST", "/api/auth/register", json = """{"name":"$prefix User","email":"$email","password":"secret123"}""")
            .andExpect(status().isCreated).body().at("$.token")
    }

    private fun createTrip(token: String, dest: String = "bali", days: Long = 2): String {
        val start = LocalDate.now().plusDays(10)
        return send("POST", "/api/trips", token, """{"destinationId":"$dest","startDate":"$start","endDate":"${start.plusDays(days)}","travelers":2}""")
            .andExpect(status().isCreated).body().at("$.id")
    }

    // ------------------------------------------------------------------ tests

    @Test
    fun `catalogue is public, filterable and has map coordinates`() {
        send("GET", "/api/destinations").andExpect(status().isOk).andExpect(jsonPath("$", hasSize<Any>(12)))
        send("GET", "/api/destinations?category=beach&sort=price_low")
            .andExpect(jsonPath("$[*].category", everyItem(`is`("beach"))))
            .andExpect(jsonPath("$[0].id", `is`("coxs-bazar")))
            .andExpect(jsonPath("$[0].isTrending", `is`(true)))
        send("GET", "/api/destinations/sajek")
            .andExpect(jsonPath("$.attractions", hasSize<Any>(3)))
            .andExpect(jsonPath("$.cost.hotelPerNight", `is`(3500)))
            .andExpect(jsonPath("$.latitude", closeTo(23.38, 0.01)))
            .andExpect(jsonPath("$.attractions[0].latitude").isNumber)
        send("GET", "/api/destinations?full=true")
            .andExpect(jsonPath("$", hasSize<Any>(12)))
            .andExpect(jsonPath("$[0].attractions").isArray)
        send("GET", "/api/hotels?destinationId=coxs-bazar")
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].latitude").isNumber)
        send("GET", "/api/destinations/nowhere").andExpect(status().isNotFound)
    }

    @Test
    fun `protected endpoints require a valid JWT`() {
        send("GET", "/api/trips").andExpect(status().isUnauthorized)
        send("GET", "/api/trips", token = "not-a-token").andExpect(status().isUnauthorized)
    }

    @Test
    fun `demo login works and a wrong password is rejected`() {
        send("POST", "/api/auth/login", json = """{"email":"demo@wanderly.app","password":"wanderly123"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.token", not(emptyString())))
            .andExpect(jsonPath("$.user.name", `is`("Demo Traveller")))
        send("POST", "/api/auth/login", json = """{"email":"demo@wanderly.app","password":"nope"}""")
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun `duplicate registration is rejected`() {
        send("POST", "/api/auth/register", json = """{"name":"Dup Licate","email":"dupe@example.com","password":"secret123"}""")
            .andExpect(status().isCreated)
        send("POST", "/api/auth/register", json = """{"name":"X Y","email":"dupe@example.com","password":"secret123"}""")
            .andExpect(status().isConflict)
    }

    @Test
    fun `trip itinerary lifecycle`() {
        val token = register("planner")
        val tripId = createTrip(token, days = 2)
        send("GET", "/api/trips/$tripId", token)
            .andExpect(jsonPath("$.title", `is`("Bali Trip")))
            .andExpect(jsonPath("$.days", hasSize<Any>(3)))
            .andExpect(jsonPath("$.role", `is`("owner")))

        val base = "/api/trips/$tripId/days/1/activities"
        val a1: String = send("POST", base, token, """{"title":"Rice terraces","time":"09:00","type":"sightseeing","cost":1500}""")
            .andExpect(status().isCreated).body().at("$.id")
        val a2: String = send("POST", base, token, """{"title":"Babi guling lunch","time":"13:00","type":"food","cost":800}""").body().at("$.id")
        send("POST", "/api/trips/$tripId/days/2/activities", token, """{"title":"Free walk","time":"18:00","type":"relax"}""")
            .andExpect(status().isCreated)
        send("POST", base, token, """{"title":"Bad time","time":"25:00","type":"food"}""").andExpect(status().isBadRequest)

        send("PUT", "$base/order", token, """{"activityIds":["$a2","$a1"]}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days[0].activities[0].title", `is`("Babi guling lunch")))
            .andExpect(jsonPath("$.plannedCost", `is`(2300)))

        send("DELETE", "$base/$a1", token).andExpect(status().isNoContent)
        send("GET", "/api/trips/$tripId", token).andExpect(jsonPath("$.activityCount", `is`(2)))

        // Someone else can't see it
        send("GET", "/api/trips/$tripId", register("stranger")).andExpect(status().isNotFound)
    }

    @Test
    fun `group trip - share code, join, shared editing, owner-only delete`() {
        val owner = register("owner")
        val friend = register("friend")
        val tripId = createTrip(owner, "sajek")

        val code: String = send("POST", "/api/trips/$tripId/share", owner).andExpect(status().isOk).body().at("$.code")
        send("POST", "/api/trips/$tripId/share", friend).andExpect(status().isNotFound) // not a member yet
        send("POST", "/api/trips/join", friend, """{"code":"${code.lowercase()}"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.role", `is`("member")))
            .andExpect(jsonPath("$.memberCount", `is`(2)))
            .andExpect(jsonPath("$.shareCode").doesNotExist())
        send("POST", "/api/trips/join", friend, """{"code":"$code"}""").andExpect(status().isConflict)

        // The companion can edit the itinerary and sees the trip in their list
        send("POST", "/api/trips/$tripId/days/1/activities", friend, """{"title":"Bonfire","time":"20:00","type":"relax"}""")
            .andExpect(status().isCreated)
        send("GET", "/api/trips", friend).andExpect(jsonPath("$[*].id", hasItem(tripId)))
        send("GET", "/api/trips/$tripId/members", owner)
            .andExpect(jsonPath("$", hasSize<Any>(2)))
            .andExpect(jsonPath("$[0].role", `is`("owner")))

        // Only the owner can delete; the owner hears about the new companion
        send("DELETE", "/api/trips/$tripId", friend).andExpect(status().isForbidden)
        send("GET", "/api/notifications", owner).andExpect(jsonPath("$[*].title", hasItem(containsString("joined"))))
        send("DELETE", "/api/trips/$tripId/members/me", friend).andExpect(status().isNoContent)
        send("GET", "/api/trips/$tripId", friend).andExpect(status().isNotFound)
    }

    @Test
    fun `booking is paid through the gateway and validated server-side`() {
        val token = register("booker")
        val checkIn = LocalDate.now().plusDays(5)
        val booking = send("POST", "/api/bookings", token, """{"hotelId":"h-ocean","checkIn":"$checkIn","checkOut":"${checkIn.plusDays(2)}","guests":2,"rooms":1}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.total", `is`(8360))) // 3800 × 2 nights + 10% tax
            .andExpect(jsonPath("$.paymentStatus", `is`("unpaid")))
            .body()
        val bookingId: String = booking.at("$.id")

        val session = send("POST", "/api/payments/bookings/$bookingId/session", token)
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.gatewayUrl", startsWith("https://")))
            .andExpect(jsonPath("$.amount", `is`(8360)))
            .andExpect(jsonPath("$.currency", `is`("BDT")))
            .body()
        val tranId: String = session.at("$.tranId")
        // Callback URLs point back at this API
        assert(gateway.sessions[tranId]!!.successUrl.endsWith("/api/payments/sslcommerz/success"))

        // A forged callback (unknown val_id) must not mark the booking paid
        mvc.perform(post("/api/payments/sslcommerz/success").param("tran_id", tranId).param("val_id", "forged"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", containsString("status=failed")))
        send("GET", "/api/bookings/$bookingId", token).andExpect(jsonPath("$.paymentStatus", `is`("failed")))

        // Retry → genuine callback validated with the gateway → paid
        val retry: String = send("POST", "/api/payments/bookings/$bookingId/session", token).body().at("$.tranId")
        mvc.perform(post("/api/payments/sslcommerz/success").param("tran_id", retry).param("val_id", "val-$retry"))
            .andExpect(status().isSeeOther)
            .andExpect(header().string("Location", containsString("status=success")))
        send("GET", "/api/bookings/$bookingId", token)
            .andExpect(jsonPath("$.paymentStatus", `is`("paid")))
            .andExpect(jsonPath("$.paymentMethod", `is`("BKASH-BKash")))
        // IPN arriving afterwards is idempotent
        mvc.perform(post("/api/payments/sslcommerz/ipn").param("tran_id", retry).param("val_id", "val-$retry").param("status", "VALID"))
            .andExpect(status().isOk)
        send("POST", "/api/payments/bookings/$bookingId/session", token).andExpect(status().isConflict) // already paid
        mvc.perform(get("/payments/result").param("status", "success").param("bookingId", bookingId))
            .andExpect(content().string(containsString("Payment successful")))
        send("GET", "/api/notifications", token).andExpect(jsonPath("$[*].title", hasItem(startsWith("Payment received"))))
    }

    @Test
    fun `planner result can be saved as a trip`() {
        val plan = send("POST", "/api/ai/plan", json = """{"prompt":"Plan a 4-day trip to Cox's Bazar under ৳20,000"}""")
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.plan.destination.id", `is`("coxs-bazar")))
            .andExpect(jsonPath("$.plan.days", `is`(4)))
            .andExpect(jsonPath("$.plan.requestedBudget", `is`(20000)))
            .andExpect(jsonPath("$.plan.itinerary", hasSize<Any>(4)))
            .andExpect(jsonPath("$.plan.itinerary[0].activities[0].costOrZero").doesNotExist())
            .body()
        val itinerary: Any = plan.at("$.plan.itinerary")
        val days = tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(itinerary)
        val start = LocalDate.now().plusDays(30)
        send("POST", "/api/trips", register("ai"), """{"destinationId":"coxs-bazar","title":"AI plan","startDate":"$start","endDate":"${start.plusDays(3)}","travelers":1,"days":$days}""")
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.activityCount", greaterThan(8)))
    }

    @Test
    fun `budget alerts, wishlist, devices and notifications`() {
        val token = register("budget")
        send("PUT", "/api/budget", token, """{"total":10000}""").andExpect(status().isOk)
        send("POST", "/api/expenses", token, """{"title":"Hotel","amount":8500,"category":"hotel"}""").andExpect(status().isCreated)
        send("GET", "/api/budget", token)
            .andExpect(jsonPath("$.spent", `is`(8500)))
            .andExpect(jsonPath("$.isNearLimit", `is`(true)))
            .andExpect(jsonPath("$.byCategory.hotel", `is`(8500)))

        send("PUT", "/api/wishlist/kyoto", token).andExpect(status().isNoContent)
        send("GET", "/api/wishlist/ids", token).andExpect(jsonPath("$", contains("kyoto")))

        send("POST", "/api/devices", token, """{"token":"fcm-token-123","platform":"android"}""").andExpect(status().isNoContent)
        send("POST", "/api/devices/unregister", token, """{"token":"fcm-token-123"}""").andExpect(status().isNoContent)

        // welcome + 80% budget alert
        send("GET", "/api/notifications", token).andExpect(jsonPath("$", hasSize<Any>(2)))
        send("GET", "/api/users/me/stats", token).andExpect(jsonPath("$.saved", `is`(1)))
    }

    @Test
    fun `tools and OpenAPI docs work`() {
        send("GET", "/api/currency/convert?from=USD&to=BDT&amount=10").andExpect(jsonPath("$.result", `is`(1220.0)))
        send("GET", "/api/packing?destinationId=maldives&days=5")
            .andExpect(jsonPath("$.sections[0].items", hasItem("Passport (6+ months validity)")))
        send("GET", "/api/destinations/coxs-bazar/weather").andExpect(jsonPath("$.days", hasSize<Any>(5)))
        send("GET", "/v3/api-docs").andExpect(status().isOk).andExpect(jsonPath("$.paths['/api/trips']").exists())
    }
}
