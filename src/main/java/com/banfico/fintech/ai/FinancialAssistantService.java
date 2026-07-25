package com.banfico.fintech.ai;

import com.banfico.fintech.common.Masking;
import com.banfico.fintech.insights.AnomalyTransaction;
import com.banfico.fintech.insights.CategoryBreakdownResponse;
import com.banfico.fintech.insights.HealthSummary;
import com.banfico.fintech.insights.InsightsService;
import com.banfico.fintech.insights.SpendingSummary;
import com.banfico.fintech.insights.TrendResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Wraps every ChatClient (Ollama) call with fallback handling — this is a live third-party-ish
 * dependency during judging, and a slow/unreachable/malformed-output model response must never
 * surface as a 500. Sandbox calls that feed the coaching-tip prompt are NOT wrapped here — they
 * already have their own Resilience4j retry/circuit-breaker (Phase 2) and a real failure there
 * (e.g. session expired) should propagate as a normal 401, not be swallowed.
 */
@Slf4j
@Service
public class FinancialAssistantService {

    private final ChatClient chatClient;
    private final ChatMemory chatMemory;
    private final FinancialTools financialTools;
    private final InsightsService insightsService;

    public FinancialAssistantService(ChatClient chatClient, ChatMemory chatMemory, FinancialTools financialTools,
                                      InsightsService insightsService) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
        this.financialTools = financialTools;
        this.insightsService = insightsService;
    }

    public ChatResponse chat(String sessionId, String message, String conversationId) {
        String resolvedConversationId = (conversationId == null || conversationId.isBlank()) ? sessionId : conversationId;
        try {
            String reply = chatClient.prompt()
                    .user(message)
                    .tools(financialTools)
                    .toolContext(Map.of(FinancialTools.SESSION_ID_KEY, sessionId))
                    .advisors(a -> a
                            .advisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                            .param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
                    .call()
                    .content();
            return new ChatResponse(reply, resolvedConversationId);
        } catch (Exception ex) {
            log.error("AI chat call failed sessionId={} conversationId={}", Masking.truncate(sessionId), resolvedConversationId, ex);
            return new ChatResponse("Sorry, I couldn't process that just now — please try again in a moment.", resolvedConversationId);
        }
    }

    public CoachingTipsResponse coachingTip(String sessionId) {
        YearMonth currentMonth = YearMonth.now();
        SpendingSummary summary = insightsService.spendingSummary(sessionId, currentMonth);
        List<AnomalyTransaction> anomalies = insightsService.anomalies(sessionId);
        HealthSummary health = insightsService.healthSummary(sessionId);

        String prompt = """
                Based on this user's real financial data for %s, give 2-3 short, specific,
                actionable financial coaching tips. Each tip must be one concise sentence and
                reference the actual numbers below where relevant — do not give generic advice.

                Current month income: %s %s
                Current month expense: %s %s
                Net change: %s %s
                Total balance across all accounts: %s %s
                Savings rate: %s
                Top spending category: %s
                Unusual transactions detected this month: %d
                """.formatted(
                currentMonth, summary.totalIncome(), summary.currency(), summary.totalExpense(), summary.currency(),
                summary.netChange(), summary.currency(), health.totalBalance(), health.currency(),
                health.savingsRatePercent() == null ? "n/a (no income this month)" : health.savingsRatePercent() + "%",
                health.topCategory() == null ? "none" : health.topCategory(), anomalies.size());

        try {
            CoachingTipsResponse response = chatClient.prompt().user(prompt).call().entity(CoachingTipsResponse.class);
            if (response == null || response.tips() == null || response.tips().isEmpty()) {
                log.warn("AI coaching-tip returned no tips, falling back to rule-based observations sessionId={}", Masking.truncate(sessionId));
                return new CoachingTipsResponse(health.observations());
            }
            return response;
        } catch (Exception ex) {
            log.error("AI coaching-tip call failed sessionId={}, falling back to rule-based observations", Masking.truncate(sessionId), ex);
            return new CoachingTipsResponse(health.observations());
        }
    }

    /**
     * Personalized recommendations grounded in the user's full recent history — a 6-month
     * income/expense (Credit/Debit) trend plus current-month category breakdown — rather than
     * just the current month's snapshot used by coachingTip. Sandbox calls here are NOT wrapped
     * in the fallback try/catch (see class javadoc); only the model call is.
     */
    public RecommendationsResponse recommendations(String sessionId) {
        YearMonth currentMonth = YearMonth.now();
        TrendResponse trend = insightsService.trend(sessionId, 6);
        CategoryBreakdownResponse categories = insightsService.categoryBreakdown(sessionId, currentMonth);
        HealthSummary health = insightsService.healthSummary(sessionId);
        List<AnomalyTransaction> anomalies = insightsService.anomalies(sessionId);

        String trendLines = trend.months().stream()
                .map(m -> "  %s: income=%s, expense=%s, net=%s (%d transactions)".formatted(
                        m.month(), m.totalIncome(), m.totalExpense(), m.netChange(), m.transactionCount()))
                .collect(Collectors.joining("\n"));
        String categoryLines = categories.categories().isEmpty()
                ? "  (no transactions this month)"
                : categories.categories().stream()
                        .map(c -> "  %s: %s %s (%s%% of this month's total, %d transactions)".formatted(
                                c.category(), c.totalAmount(), categories.currency(), c.percentageOfTotal(), c.transactionCount()))
                        .collect(Collectors.joining("\n"));

        String prompt = """
                Based on this user's real financial history, give 3-5 personalized, specific,
                actionable financial recommendations (budgeting, savings goals, spending alerts).
                Each recommendation needs a short title, a one-to-two sentence description that
                references the actual numbers below, an optional spending category it relates to
                (or omit/null if it's general, e.g. a savings goal), and a priority of "high",
                "medium", or "low". Do not invent numbers or give generic advice unrelated to the
                data below — if the data is too sparse for a given angle, skip it rather than
                guessing.

                Income vs expense (Credit vs Debit) trend, last 6 months, currency %s:
                %s

                Current month (%s) category breakdown:
                %s

                Total balance across all accounts: %s %s
                Current savings rate: %s
                Unusual transactions detected (all-time): %d
                """.formatted(
                trend.currency(), trendLines, currentMonth, categoryLines,
                health.totalBalance(), health.currency(),
                health.savingsRatePercent() == null ? "n/a (no income this month)" : health.savingsRatePercent() + "%",
                anomalies.size());

        try {
            RecommendationsResponse response = chatClient.prompt().user(prompt).call().entity(RecommendationsResponse.class);
            if (response == null || response.recommendations() == null || response.recommendations().isEmpty()) {
                log.warn("AI recommendations returned nothing, falling back to rule-based sessionId={}", Masking.truncate(sessionId));
                return new RecommendationsResponse(financialTools.buildRuleBasedRecommendations(sessionId));
            }
            return response;
        } catch (Exception ex) {
            log.error("AI recommendations call failed sessionId={}, falling back to rule-based", Masking.truncate(sessionId), ex);
            return new RecommendationsResponse(financialTools.buildRuleBasedRecommendations(sessionId));
        }
    }
}
