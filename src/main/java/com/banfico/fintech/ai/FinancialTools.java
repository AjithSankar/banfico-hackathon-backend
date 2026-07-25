package com.banfico.fintech.ai;

import com.banfico.fintech.account.AccountService;
import com.banfico.fintech.account.AccountSummary;
import com.banfico.fintech.common.Masking;
import com.banfico.fintech.insights.AnomalyTransaction;
import com.banfico.fintech.insights.CategoryBreakdownResponse;
import com.banfico.fintech.insights.HealthSummary;
import com.banfico.fintech.insights.InsightsService;
import com.banfico.fintech.insights.OverspendingAlert;
import com.banfico.fintech.insights.SpendingSummary;
import com.banfico.fintech.insights.SubscriptionCandidate;
import com.banfico.fintech.insights.TrendResponse;
import com.banfico.fintech.transaction.TransactionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Tool/function-calling surface for the AI chat assistant. Every method reads the caller's
 * sessionId from ToolContext (populated per-call in FinancialAssistantService) rather than a
 * global, so tools hit the sandbox with the right user's data — these are thin delegations to
 * AccountService/InsightsService, never reimplemented logic.
 */
@Slf4j
@Component
public class FinancialTools {

    public static final String SESSION_ID_KEY = "sessionId";

    private final AccountService accountService;
    private final InsightsService insightsService;

    public FinancialTools(AccountService accountService, InsightsService insightsService) {
        this.accountService = accountService;
        this.insightsService = insightsService;
    }

    @Tool(description = "Get the current balance of every one of the user's bank accounts")
    public List<AccountSummary> getAccountBalances(ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        log.debug("Tool getAccountBalances sessionId={}", Masking.truncate(sessionId));
        return accountService.listAccounts(sessionId);
    }

    @Tool(description = "Get the user's transactions filtered by spending category, optionally restricted to one month")
    public List<TransactionSummary> getTransactionsByCategory(
            @ToolParam(description = "Category name, e.g. Groceries, Dining, Utilities, Transport, Subscriptions") String category,
            @ToolParam(required = false, description = "Month in YYYY-MM format; omit to search all history") String month,
            ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        YearMonth parsedMonth = (month == null || month.isBlank()) ? null : YearMonth.parse(month);
        log.debug("Tool getTransactionsByCategory sessionId={} category={} month={}", Masking.truncate(sessionId), category, month);
        return insightsService.transactionsByCategory(sessionId, category, parsedMonth);
    }

    @Tool(description = "Get total income, total expense and net change for a given month; defaults to the current month")
    public SpendingSummary getSpendingSummary(
            @ToolParam(required = false, description = "Month in YYYY-MM format; omit for the current month") String month,
            ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        YearMonth parsedMonth = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        log.debug("Tool getSpendingSummary sessionId={} month={}", Masking.truncate(sessionId), parsedMonth);
        return insightsService.spendingSummary(sessionId, parsedMonth);
    }

    @Tool(description = "Get transactions flagged as unusual compared to the user's normal spending in that category")
    public List<AnomalyTransaction> getAnomalies(ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        log.debug("Tool getAnomalies sessionId={}", Masking.truncate(sessionId));
        return insightsService.anomalies(sessionId);
    }

    @Tool(description = "Get spending broken down by category for a given month, with each category's share of the month's total; defaults to the current month")
    public CategoryBreakdownResponse getCategoryBreakdown(
            @ToolParam(required = false, description = "Month in YYYY-MM format; omit for the current month") String month,
            ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        YearMonth parsedMonth = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        log.debug("Tool getCategoryBreakdown sessionId={} month={}", Masking.truncate(sessionId), parsedMonth);
        return insightsService.categoryBreakdown(sessionId, parsedMonth);
    }

    @Tool(description = "Get month-by-month income, expense, and net change over a recent window (e.g. the last 6 months) to see spending/income trajectory over time")
    public TrendResponse getSpendingTrend(
            @ToolParam(required = false, description = "Number of months to look back, including the current month; defaults to 6") Integer months,
            ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        int resolvedMonths = months == null ? 6 : months;
        log.debug("Tool getSpendingTrend sessionId={} months={}", Masking.truncate(sessionId), resolvedMonths);
        return insightsService.trend(sessionId, resolvedMonths);
    }

    @Tool(description = "Get recurring subscription-like charges detected from transaction history: same merchant, similar amount, roughly monthly")
    public List<SubscriptionCandidate> getSubscriptions(ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        log.debug("Tool getSubscriptions sessionId={}", Masking.truncate(sessionId));
        return insightsService.subscriptions(sessionId);
    }

    @Tool(description = "Get categories where spending increased sharply (20%+ or 50%+) vs. the previous month, or brand-new spending categories — the main signal for \"am I overspending\" questions")
    public List<OverspendingAlert> getOverspendingAlerts(
            @ToolParam(required = false, description = "Month in YYYY-MM format to check against the prior month; omit for the current month") String month,
            ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        YearMonth parsedMonth = (month == null || month.isBlank()) ? YearMonth.now() : YearMonth.parse(month);
        log.debug("Tool getOverspendingAlerts sessionId={} month={}", Masking.truncate(sessionId), parsedMonth);
        return insightsService.overspendingAlerts(sessionId, parsedMonth);
    }

    @Tool(description = "Get an overall financial health snapshot: total balance, this month's income/expense/net, savings rate, top spending category, and plain-language observations")
    public HealthSummary getHealthSummary(ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        log.debug("Tool getHealthSummary sessionId={}", Masking.truncate(sessionId));
        return insightsService.healthSummary(sessionId);
    }

    @Tool(description = "Get personalized financial recommendations (budgeting, savings, spending alerts) based on the user's financial health")
    public List<Recommendation> getPersonalizedRecommendations(ToolContext toolContext) {
        String sessionId = sessionId(toolContext);
        log.debug("Tool getPersonalizedRecommendations sessionId={}", Masking.truncate(sessionId));
        return buildRuleBasedRecommendations(sessionId);
    }

    /**
     * Deterministic, non-AI recommendation builder — shared by the getPersonalizedRecommendations
     * tool above (so the conversational /api/ai/chat flow can call it directly) and by
     * FinancialAssistantService.recommendations()'s fallback when the model call fails. A @Tool
     * method calling back into the ChatClient itself would mean a nested AI call inside a tool
     * invocation, which this deliberately avoids — tools stay real-data-only.
     */
    List<Recommendation> buildRuleBasedRecommendations(String sessionId) {
        HealthSummary health = insightsService.healthSummary(sessionId);
        List<Recommendation> recommendations = new ArrayList<>();
        for (String observation : health.observations()) {
            recommendations.add(new Recommendation("Financial health observation", observation, health.topCategory(), "medium"));
        }
        return recommendations;
    }

    private String sessionId(ToolContext toolContext) {
        Object value = toolContext.getContext().get(SESSION_ID_KEY);
        if (value == null) {
            throw new IllegalStateException("Tool invoked without a sessionId in ToolContext");
        }
        return value.toString();
    }
}
