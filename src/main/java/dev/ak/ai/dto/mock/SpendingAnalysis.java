package dev.ak.ai.dto.mock;

import java.util.List;

public record SpendingAnalysis(
    String topCategory,
    List<String> flaggedSubscriptions,
    List<String> smartInsights
) {}