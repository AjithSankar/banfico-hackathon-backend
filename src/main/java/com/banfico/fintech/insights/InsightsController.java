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
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.spendingSummary(CurrentSession.sessionId(), month == null ? YearMonth.now() : month, accountId));
    }

    @GetMapping("/category-breakdown")
    public ApiResponse<CategoryBreakdownResponse> categoryBreakdown(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.categoryBreakdown(CurrentSession.sessionId(), month == null ? YearMonth.now() : month, accountId));
    }

    @GetMapping("/trend")
    public ApiResponse<TrendResponse> trend(@RequestParam(defaultValue = "6") int months,
                                             @RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.trend(CurrentSession.sessionId(), months, accountId));
    }

    @GetMapping("/anomalies")
    public ApiResponse<List<AnomalyTransaction>> anomalies(@RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.anomalies(CurrentSession.sessionId(), accountId));
    }

    @GetMapping("/health-summary")
    public ApiResponse<HealthSummary> healthSummary(@RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.healthSummary(CurrentSession.sessionId(), accountId));
    }

    @GetMapping("/subscriptions")
    public ApiResponse<List<SubscriptionCandidate>> subscriptions(@RequestParam(required = false) String accountId) {
        return ApiResponse.ok(insightsService.subscriptions(CurrentSession.sessionId(), accountId));
    }
}
