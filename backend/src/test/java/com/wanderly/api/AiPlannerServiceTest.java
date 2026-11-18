package com.wanderly.api;

import com.wanderly.api.service.AiPlannerService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AiPlannerServiceTest extends AiPlannerServiceAccess {

    @Test
    void parsesDaysNightsAndWeekends() {
        assertThat(days("plan a 4-day trip")).contains(4);
        assertThat(days("3 nights in bali")).contains(4);
        assertThat(days("a weekend away")).contains(2);
        assertThat(days("somewhere nice")).isEmpty();
    }

    @Test
    void parsesBudgetsInTakaThousandsAndLakh() {
        assertThat(budget("under ৳20,000")).contains(20000);
        assertThat(budget("budget 25k")).contains(25000);
        assertThat(budget("budget 2 lakh")).contains(200000);
        assertThat(budget("15000 tk")).contains(15000);
        assertThat(budget("4 day trip")).isEmpty();
    }

    @Test
    void parsesTravellers() {
        assertThat(travelers("weekend in sajek for 4 friends")).contains(4);
        assertThat(travelers("honeymoon in maldives")).contains(2);
        assertThat(travelers("solo trip")).isEmpty();
    }
}

/** Exposes the package-private parsing helpers to the test. */
abstract class AiPlannerServiceAccess {
    static java.util.Optional<Integer> days(String p) {
        return invoke("parseDays", p);
    }

    static java.util.Optional<Integer> budget(String p) {
        return invoke("parseBudget", p);
    }

    static java.util.Optional<Integer> travelers(String p) {
        return invoke("parseTravelers", p);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Optional<Integer> invoke(String method, String p) {
        try {
            var m = AiPlannerService.class.getDeclaredMethod(method, String.class);
            m.setAccessible(true);
            return (java.util.Optional<Integer>) m.invoke(null, p);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
