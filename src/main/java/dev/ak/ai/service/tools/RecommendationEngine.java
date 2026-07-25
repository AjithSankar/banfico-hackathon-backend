package dev.ak.ai.service.tools;


import dev.ak.ai.dto.mock.CategorySpend;
import dev.ak.ai.dto.mock.Recommendation;
import dev.ak.ai.dto.mock.RecommendationsResponse;
import dev.ak.ai.dto.mock.SpendingInsightsResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RecommendationEngine {

    private final SpendingInsightsService insightsService;

    private static final double OVERSPEND_THRESHOLD = 2000.0;
    private static final double LOW_SAVINGS_RATE_THRESHOLD = 20.0;
    private static final int SUBSCRIPTION_COUNT_WARNING = 2;

    public RecommendationEngine(SpendingInsightsService insightsService) {
        this.insightsService = insightsService;
    }

    public RecommendationsResponse generateRecommendations() {
        SpendingInsightsResponse insights = insightsService.computeInsights();
        List<Recommendation> recommendations = new ArrayList<>();

        if (insights.recurringSubscriptions().size() >= SUBSCRIPTION_COUNT_WARNING) {
            recommendations.add(new Recommendation(
                    "SUBSCRIPTION_OPTIMIZATION",
                    "Review your active subscriptions",
                    "You have " + insights.recurringSubscriptions().size() +
                            " recurring subscriptions (" + String.join(", ", insights.recurringSubscriptions()) +
                            "). Consider cancelling ones you rarely use to save monthly costs.",
                    "MEDIUM"
            ));
        }

        for (CategorySpend cs : insights.categoryBreakdown()) {
            if (cs.totalAmount() > OVERSPEND_THRESHOLD) {
                recommendations.add(new Recommendation(
                        "OVERSPENDING_ALERT",
                        "High spending in " + cs.category(),
                        "You've spent ₹" + cs.totalAmount() + " on " + cs.category() +
                                " recently across " + cs.transactionCount() + " transactions. " +
                                "Consider setting a monthly budget for this category.",
                        "HIGH"
                ));
            }
        }

        if (insights.savingsRate() < LOW_SAVINGS_RATE_THRESHOLD && insights.totalIncome() > 0) {
            recommendations.add(new Recommendation(
                    "SAVINGS_IMPROVEMENT",
                    "Your savings rate is low",
                    "You're saving only " + insights.savingsRate() + "% of your income. " +
                            "Financial experts generally recommend saving at least 20%. " +
                            "Consider setting up an automatic transfer to your savings account.",
                    "HIGH"
            ));
        }

        if (recommendations.isEmpty()) {
            recommendations.add(new Recommendation(
                    "POSITIVE_REINFORCEMENT",
                    "You're on track",
                    "Your spending and savings patterns look healthy this period. Keep it up!",
                    "LOW"
            ));
        }

        return new RecommendationsResponse(recommendations);
    }
}