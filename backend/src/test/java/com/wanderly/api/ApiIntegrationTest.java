package com.wanderly.api;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full-stack test: real Spring context, H2 database, JWT security, seeded data. */
@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "wanderly.uploads.dir=target/test-uploads")
class ApiIntegrationTest {

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ResultActions post(String url, String token, String json) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post(url)
                .contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) req.header("Authorization", "Bearer " + token);
        return mvc.perform(req);
    }

    private String register(String email) throws Exception {
        String body = post("/api/auth/register", null,
                "{\"name\":\"Test User\",\"email\":\"" + email + "\",\"password\":\"secret123\"}")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    @Test
    void catalogueIsPublicAndFilterable() throws Exception {
        mvc.perform(get("/api/destinations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(12)));
        mvc.perform(get("/api/destinations").param("category", "beach").param("sort", "price_low"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category", everyItem(is("beach"))))
                .andExpect(jsonPath("$[0].id", is("coxs-bazar")));
        mvc.perform(get("/api/destinations/sajek"))
                .andExpect(jsonPath("$.attractions", hasSize(3)))
                .andExpect(jsonPath("$.cost.hotelPerNight", is(3500)));
        mvc.perform(get("/api/hotels").param("destinationId", "coxs-bazar"))
                .andExpect(jsonPath("$", hasSize(2)));
        mvc.perform(get("/api/destinations/nowhere")).andExpect(status().isNotFound());
        mvc.perform(get("/api/destinations").param("full", "true"))
                .andExpect(jsonPath("$", hasSize(12)))
                .andExpect(jsonPath("$[0].attractions").isArray())
                .andExpect(jsonPath("$[0].cost.transport").isNumber());
    }

    @Test
    void protectedEndpointsRequireJwt() throws Exception {
        mvc.perform(get("/api/trips")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/trips").header("Authorization", "Bearer not-a-token")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWorksForDemoUserAndRejectsBadPassword() throws Exception {
        post("/api/auth/login", null, "{\"email\":\"demo@wanderly.app\",\"password\":\"wanderly123\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", not(emptyString())))
                .andExpect(jsonPath("$.user.name", is("Demo Traveller")));
        post("/api/auth/login", null, "{\"email\":\"demo@wanderly.app\",\"password\":\"nope\"}")
                .andExpect(status().isUnauthorized());
    }

    @Test
    void duplicateRegistrationIsRejected() throws Exception {
        register("dupe@example.com");
        post("/api/auth/register", null, "{\"name\":\"X Y\",\"email\":\"dupe@example.com\",\"password\":\"secret123\"}")
                .andExpect(status().isConflict());
    }

    @Test
    void tripItineraryLifecycle() throws Exception {
        String token = register("planner@example.com");
        LocalDate start = LocalDate.now().plusDays(10);

        String trip = post("/api/trips", token, """
                {"destinationId":"bali","startDate":"%s","endDate":"%s","travelers":2}
                """.formatted(start, start.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title", is("Bali Trip")))
                .andExpect(jsonPath("$.days", hasSize(3)))
                .andReturn().getResponse().getContentAsString();
        String tripId = JsonPath.read(trip, "$.id");

        String base = "/api/trips/" + tripId + "/days/1/activities";
        String a1 = JsonPath.read(post(base, token, "{\"title\":\"Rice terraces\",\"time\":\"09:00\",\"type\":\"sightseeing\",\"cost\":1500}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.id");
        String a2 = JsonPath.read(post(base, token, "{\"title\":\"Babi guling lunch\",\"time\":\"13:00\",\"type\":\"food\",\"cost\":800}")
                .andReturn().getResponse().getContentAsString(), "$.id");
        post("/api/trips/" + tripId + "/days/2/activities", token, "{\"title\":\"Free walk\",\"time\":\"18:00\",\"type\":\"relax\"}").andExpect(status().isCreated());
        post(base, token, "{\"title\":\"Bad time\",\"time\":\"25:00\",\"type\":\"food\"}").andExpect(status().isBadRequest());

        mvc.perform(put(base + "/order").header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"activityIds\":[\"" + a2 + "\",\"" + a1 + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].activities[0].title", is("Babi guling lunch")))
                .andExpect(jsonPath("$.plannedCost", is(2300)));

        mvc.perform(delete(base + "/" + a1).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/trips/" + tripId).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.activityCount", is(2))); // 1 left on day 1 + "Free walk" on day 2

        // Another user cannot see this trip
        String other = register("other@example.com");
        mvc.perform(get("/api/trips/" + tripId).header("Authorization", "Bearer " + other))
                .andExpect(status().isNotFound());
    }

    @Test
    void aiPlanCanBeSavedAsTrip() throws Exception {
        String plan = post("/api/ai/plan", null, "{\"prompt\":\"Plan a 4-day trip to Cox's Bazar under ৳20,000\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.destination.id", is("coxs-bazar")))
                .andExpect(jsonPath("$.plan.days", is(4)))
                .andExpect(jsonPath("$.plan.requestedBudget", is(20000)))
                .andExpect(jsonPath("$.plan.itinerary", hasSize(4)))
                .andReturn().getResponse().getContentAsString();

        List<Object> itinerary = JsonPath.read(plan, "$.plan.itinerary");
        String token = register("ai@example.com");
        LocalDate start = LocalDate.now().plusDays(30);
        String days = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(itinerary);
        post("/api/trips", token, """
                {"destinationId":"coxs-bazar","title":"AI plan","startDate":"%s","endDate":"%s","travelers":1,"days":%s}
                """.formatted(start, start.plusDays(3), days))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.activityCount", greaterThan(8)));
    }

    @Test
    void bookingBudgetWishlistAndNotifications() throws Exception {
        String token = register("booker@example.com");
        LocalDate in = LocalDate.now().plusDays(5);

        post("/api/bookings", token, """
                {"hotelId":"h-ocean","checkIn":"%s","checkOut":"%s","guests":2,"rooms":1}
                """.formatted(in, in.plusDays(2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", startsWith("WND-")))
                .andExpect(jsonPath("$.total", is(8360))); // 3800 × 2 nights + 10% tax

        mvc.perform(put("/api/budget").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"total\":10000}")).andExpect(status().isOk());
        post("/api/expenses", token, "{\"title\":\"Hotel\",\"amount\":8500,\"category\":\"hotel\"}")
                .andExpect(status().isCreated());
        mvc.perform(get("/api/budget").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.spent", is(8500)))
                .andExpect(jsonPath("$.isNearLimit", is(true)))
                .andExpect(jsonPath("$.byCategory.hotel", is(8500)));

        mvc.perform(put("/api/wishlist/kyoto").header("Authorization", "Bearer " + token)).andExpect(status().isNoContent());
        mvc.perform(get("/api/wishlist/ids").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", contains("kyoto")));

        // welcome + booking + 80% budget alert
        mvc.perform(get("/api/notifications").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/users/me/stats").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.bookings", is(1)))
                .andExpect(jsonPath("$.saved", is(1)));
    }

    @Test
    void openApiDocsArePublished() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/trips']").exists());
    }

    @Test
    void toolsWork() throws Exception {
        mvc.perform(get("/api/currency/convert").param("from", "USD").param("to", "BDT").param("amount", "10"))
                .andExpect(jsonPath("$.result", is(1220.0)));
        mvc.perform(get("/api/packing").param("destinationId", "maldives").param("days", "5"))
                .andExpect(jsonPath("$.sections[0].items", hasItem("Passport (6+ months validity)")));
        mvc.perform(get("/api/destinations/coxs-bazar/weather"))
                .andExpect(jsonPath("$.days", hasSize(5)));
    }
}
