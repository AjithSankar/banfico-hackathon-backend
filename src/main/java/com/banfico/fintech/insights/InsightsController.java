package com.banfico.fintech.insights;

import com.banfico.fintech.auth.CurrentSession;
import com.banfico.fintech.common.ApiResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;
import java.util.List;

@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final InsightsService insightsService;

    public InsightsController(InsightsService insightsService) {
        this.insightsService = insightsService;
    }

    @GetMapping("/spending-summary")
    public ApiResponse<SpendingSummary> spendingSummary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.ok(insightsService.spendingSummary(CurrentSession.sessionId(), month == null ? YearMonth.now() : month));
    }

    @GetMapping("/category-breakdown")
    public ApiResponse<CategoryBreakdownResponse> categoryBreakdown(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month) {
        return ApiResponse.ok(insightsService.categoryBreakdown(CurrentSession.sessionId(), month == null ? YearMonth.now() : month));
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponse> trend(@RequestParam(defaultValue = "6") int months) {
        return ApiResponse.ok(insightsService.trend(CurrentSession.sessionId(), months));
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<AnomalyTransaction>> anomalies() {
        return ApiResponse.ok(insightsService.anomalies(CurrentSession.sessionId()));
    }

    @GetMapping("/health-summary")
    public ApiResponse<HealthSummary> healthSummary() {
        return ApiResponse.ok(insightsService.healthSummary(CurrentSession.sessionId()));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SubscriptionCandidate>> subscriptions() {
        return ApiResponse.ok(insightsService.subscriptions(CurrentSession.sessionId()));
    }
}
