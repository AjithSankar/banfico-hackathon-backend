package com.banfico.fintech.ai;

import com.banfico.fintech.common.Masking;
import com.banfico.fintech.insights.AnomalyTransaction;
import com.banfico.fintech.insights.HealthSummary;
import com.banfico.fintech.insights.InsightsService;
import com.banfico.fintech.insights.SpendingSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.List;
import java.util.Map;

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
    private final FinancialTools financialTools;
    private final InsightsService insightsService;

    public FinancialAssistantService(ChatClient chatClient, FinancialTools financialTools, InsightsService insightsService) {
        this.chatClient = chatClient;
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
                    .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, resolvedConversationId))
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
}
