package dev.ak.ai.service.tools;


import dev.ak.ai.dto.mock.RecommendationsResponse;
import dev.ak.ai.dto.mock.SpendingInsightsResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class InsightsToolService {

    private final SpendingInsightsService insightsService;
    private final RecommendationEngine recommendationEngine;

    public InsightsToolService(SpendingInsightsService insightsService, RecommendationEngine recommendationEngine) {
        this.insightsService = insightsService;
        this.recommendationEngine = recommendationEngine;
    }

    @Tool(description = "Gets the user's spending insights including total spend, top spending category, savings rate, and detected recurring subscriptions.")
    public SpendingInsightsResponse getSpendingInsights() {
        log.info("getSpendingInsights() tool called..");
        SpendingInsightsResponse spendingInsightsResponse = insightsService.computeInsights();
        log.info("getSpendingInsights() tool done");
        return spendingInsightsResponse;
    }

    @Tool(description = "Gets personalized financial recommendations for the user based on their real spending and savings patterns.")
    public RecommendationsResponse getRecommendations() {
        log.info("getRecommendations() tool called..");
        RecommendationsResponse recommendationsResponse = recommendationEngine.generateRecommendations();
        log.info("getRecommendations() tool done");
        return recommendationsResponse;
    }
}