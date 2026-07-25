package dev.ak.ai.dto.mock;

import java.util.List;

public record SpendingInsightsResponse(
        double totalSpend,
        double totalIncome,
        List<CategorySpend> categoryBreakdown,
        String topCategory,
        double topCategoryAmount,
        List<String> recurringSubscriptions,
        double savingsRate
) {}