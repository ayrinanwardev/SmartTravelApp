package com.wanderly.api

import com.wanderly.api.service.AiPlannerService
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AiPlannerServiceTest {

    @Test
    fun `parses days, nights and weekends`() {
        assertThat(AiPlannerService.parseDays("plan a 4-day trip")).isEqualTo(4)
        assertThat(AiPlannerService.parseDays("3 nights in bali")).isEqualTo(4)
        assertThat(AiPlannerService.parseDays("a weekend away")).isEqualTo(2)
        assertThat(AiPlannerService.parseDays("somewhere nice")).isNull()
    }

    @Test
    fun `parses budgets in taka, thousands and lakh`() {
        assertThat(AiPlannerService.parseBudget("under ৳20,000")).isEqualTo(20000)
        assertThat(AiPlannerService.parseBudget("budget 25k")).isEqualTo(25000)
        assertThat(AiPlannerService.parseBudget("budget 2 lakh")).isEqualTo(200000)
        assertThat(AiPlannerService.parseBudget("15000 tk")).isEqualTo(15000)
        assertThat(AiPlannerService.parseBudget("4 day trip")).isNull()
    }

    @Test
    fun `parses travellers`() {
        assertThat(AiPlannerService.parseTravelers("weekend in sajek for 4 friends")).isEqualTo(4)
        assertThat(AiPlannerService.parseTravelers("honeymoon in maldives")).isEqualTo(2)
        assertThat(AiPlannerService.parseTravelers("solo trip")).isNull()
    }
}
