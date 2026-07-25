package dev.ak.ai.service.tools;


import dev.ak.ai.dto.mock.CategorySpend;
import dev.ak.ai.dto.mock.DashboardResponse;
import dev.ak.ai.dto.mock.SpendingInsightsResponse;
import dev.ak.ai.dto.mock.Transaction;
import dev.ak.ai.service.mock.DashboardService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SpendingInsightsService {

    private final DashboardService dashboardService;

    public SpendingInsightsService(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    public SpendingInsightsResponse computeInsights() {
        DashboardResponse dashboard = dashboardService.getDashboardData();
        List<Transaction> transactions = dashboard.recentTransactions();

        double totalSpend = transactions.stream()
                .filter(t -> "DEBIT".equals(t.type()))
                .mapToDouble(Transaction::amount)
                .sum();

        double totalIncome = transactions.stream()
                .filter(t -> "CREDIT".equals(t.type()))
                .mapToDouble(Transaction::amount)
                .sum();

        Map<String, List<Transaction>> byCategory = transactions.stream()
                .filter(t -> "DEBIT".equals(t.type()))
                .collect(Collectors.groupingBy(Transaction::category));

        List<CategorySpend> categoryBreakdown = byCategory.entrySet().stream()
                .map(e -> new CategorySpend(
                        e.getKey(),
                        e.getValue().stream().mapToDouble(Transaction::amount).sum(),
                        e.getValue().size()
                ))
                .sorted(Comparator.comparingDouble(CategorySpend::totalAmount).reversed())
                .toList();

        CategorySpend top = categoryBreakdown.isEmpty() ? null : categoryBreakdown.get(0);

        List<String> recurringSubscriptions = transactions.stream()
                .filter(t -> "Subscription".equals(t.category()))
                .map(Transaction::merchant)
                .distinct()
                .toList();

        double savingsRate = totalIncome == 0 ? 0 :
                ((totalIncome - totalSpend) / totalIncome) * 100;

        return new SpendingInsightsResponse(
                totalSpend,
                totalIncome,
                categoryBreakdown,
                top != null ? top.category() : "N/A",
                top != null ? top.totalAmount() : 0,
                recurringSubscriptions,
                Math.round(savingsRate * 100) / 100.0
        );
    }
}