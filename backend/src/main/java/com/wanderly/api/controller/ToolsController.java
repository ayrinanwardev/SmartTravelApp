package com.wanderly.api.controller;

import com.wanderly.api.dto.CommonDtos.ConversionResult;
import com.wanderly.api.dto.DestinationDtos.PackingList;
import com.wanderly.api.service.AiPlannerService;
import com.wanderly.api.service.AiPlannerService.PlanRequest;
import com.wanderly.api.service.AiPlannerService.PlanResponse;
import com.wanderly.api.service.ToolsService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** Public helper endpoints: AI planner, currency converter, packing list. */
@RestController
@RequestMapping("/api")
public class ToolsController {

    private final AiPlannerService ai;
    private final ToolsService tools;

    public ToolsController(AiPlannerService ai, ToolsService tools) {
        this.ai = ai;
        this.tools = tools;
    }

    /** Body: {"prompt": "Plan a 4-day trip to Cox's Bazar under ৳20,000"} */
    @PostMapping("/ai/plan")
    public PlanResponse plan(@RequestBody PlanRequest req) {
        if (req.prompt() == null || req.prompt().isBlank()) {
            throw new IllegalArgumentException("prompt is required");
        }
        if (req.prompt().length() > 1000) {
            throw new IllegalArgumentException("prompt must be at most 1000 characters");
        }
        return ai.plan(req.prompt());
    }

    /** 1 unit of each currency in BDT. */
    @GetMapping("/currency/rates")
    public Map<String, Double> rates() {
        return ToolsService.BDT_RATES;
    }

    /** GET /api/currency/convert?from=BDT&to=USD&amount=10000 */
    @GetMapping("/currency/convert")
    public ConversionResult convert(@RequestParam String from, @RequestParam String to, @RequestParam double amount) {
        return tools.convert(from, to, amount);
    }

    /** GET /api/packing?destinationId=sajek&days=4 */
    @GetMapping("/packing")
    public PackingList packing(@RequestParam String destinationId, @RequestParam(defaultValue = "4") int days) {
        return tools.packing(destinationId, days);
    }
}
