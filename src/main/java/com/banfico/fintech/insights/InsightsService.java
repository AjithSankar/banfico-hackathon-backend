package com.banfico.fintech.insights;

import com.banfico.fintech.account.AccountService;
import com.banfico.fintech.account.AccountSummary;
import com.banfico.fintech.account.BalanceResponse;
import com.banfico.fintech.common.Concurrency;
import com.banfico.fintech.sandbox.SandboxAisClient;
import com.banfico.fintech.sandbox.dto.ObieAccount;
import com.banfico.fintech.transaction.TransactionService;
import com.banfico.fintech.transaction.TransactionSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Computes every metric live from transactions fetched once per request (fanned out across
 * accounts concurrently) and shared across whichever sub-calculations that request needs —
 * no persistence, no caching, no repeat sandbox calls per metric.
 *
 * <p><b>Known demo-data limitations</b> (see IMPLEMENTATION_PLAN.md Phase 5 notes): the
 * Hackathon Mock Bank sandbox's seed data (a) marks essentially every transaction as
 * {@code CreditDebitIndicator=Credit}, so income-vs-expense split will look one-sided unless
 * some Debit transactions are seeded first; (b) generates a random company name per
 * transaction, so merchants rarely repeat — subscription detection will likely return empty
 * without additional seeded data; (c) many accounts only have 1-4 transactions, too few for
 * meaningful anomaly detection (which requires at least 3 per category).
 */
@Slf4j
@Service
public class InsightsService {

    private static final int MIN_TRANSACTIONS_FOR_ANOMALY_DETECTION = 3;
    private static final BigDecimal ANOMALY_STD_DEV_MULTIPLIER = BigDecimal.valueOf(2);
    private static final double SUBSCRIPTION_AMOUNT_TOLERANCE = 0.15;
    private static final double SUBSCRIPTION_MIN_INTERVAL_DAYS = 20;
    private static final double SUBSCRIPTION_MAX_INTERVAL_DAYS = 40;

    private final SandboxAisClient aisClient;
    private final TransactionService transactionService;
    private final AccountService accountService;

    public InsightsService(SandboxAisClient aisClient, TransactionService transactionService, AccountService accountService) {
        this.aisClient = aisClient;
        this.transactionService = transactionService;
        this.accountService = accountService;
    }

    public SpendingSummary spendingSummary(String sessionId, YearMonth month) {
        return spendingSummary(sessionId, month, null);
    }

    /** @param accountId optional — scope to one account's transactions instead of all accounts. */
    public SpendingSummary spendingSummary(String sessionId, YearMonth month, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);
        return computeSpendingSummary(all, month);
    }

    public CategoryBreakdownResponse categoryBreakdown(String sessionId, YearMonth month) {
        return categoryBreakdown(sessionId, month, null);
    }

    /** @param accountId optional — scope to one account's transactions instead of all accounts. */
    public CategoryBreakdownResponse categoryBreakdown(String sessionId, YearMonth month, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);
        List<TransactionSummary> monthly = filterByMonth(all, month);
        return new CategoryBreakdownResponse(month.toString(), resolveCurrency(all), computeCategoryBreakdown(monthly));
    }

    public TrendResponse trend(String sessionId, int months) {
        return trend(sessionId, months, null);
    }

    /** @param accountId optional — scope to one account's transactions instead of all accounts. */
    public TrendResponse trend(String sessionId, int months, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);
        return new TrendResponse(resolveCurrency(all), computeTrend(all, months));
    }

    public List<AnomalyTransaction> anomalies(String sessionId) {
        return anomalies(sessionId, null);
    }

    /** @param accountId optional — scope to one account's transaction history instead of all accounts. */
    public List<AnomalyTransaction> anomalies(String sessionId, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);
        return computeAnomalies(all);
    }

    public List<SubscriptionCandidate> subscriptions(String sessionId) {
        return subscriptions(sessionId, null);
    }

    /** @param accountId optional — scope recurring-charge detection to one account. */
    public List<SubscriptionCandidate> subscriptions(String sessionId, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);
        return computeSubscriptions(all);
    }

    /** Across all accounts; month is optional (null = all time). Used by the AI chat tool layer (Phase 6). */
    public List<TransactionSummary> transactionsByCategory(String sessionId, String category, YearMonth month) {
        List<TransactionSummary> all = fetchAllTransactions(sessionId);
        return all.stream()
                .filter(t -> category.equalsIgnoreCase(t.category()))
                .filter(t -> month == null || YearMonth.from(t.bookingDateTime().atZone(ZoneOffset.UTC)).equals(month))
                .sorted(Comparator.comparing(TransactionSummary::bookingDateTime).reversed())
                .toList();
    }

    public HealthSummary healthSummary(String sessionId) {
        return healthSummary(sessionId, null);
    }

    /**
     * @param accountId optional — scope to one account. When present, {@code totalBalance}/
     *                  {@code currency} reflect just that account instead of the sum/first of all
     *                  accounts (confirmed with the frontend team: this is the expected behavior).
     */
    public HealthSummary healthSummary(String sessionId, String accountId) {
        List<TransactionSummary> all = fetchTransactions(sessionId, accountId);

        BigDecimal totalBalance;
        String currency;
        if (accountId != null && !accountId.isBlank()) {
            BalanceResponse balance = accountService.getBalance(sessionId, accountId);
            totalBalance = balance.amount() == null ? BigDecimal.ZERO : balance.amount();
            currency = balance.currency() != null ? balance.currency() : resolveCurrency(all);
        } else {
            List<AccountSummary> accounts = accountService.listAccounts(sessionId);
            totalBalance = accounts.stream()
                    .map(AccountSummary::balance)
                    .filter(Objects::nonNull)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            currency = accounts.isEmpty() ? resolveCurrency(all) : accounts.getFirst().currency();
        }

        YearMonth currentMonth = YearMonth.now();
        SpendingSummary currentMonthSummary = computeSpendingSummary(all, currentMonth);
        List<CategoryBreakdown> currentMonthCategories = computeCategoryBreakdown(filterByMonth(all, currentMonth));
        List<AnomalyTransaction> currentAnomalies = computeAnomalies(all);

        BigDecimal savingsRatePercent = currentMonthSummary.totalIncome().signum() > 0
                ? currentMonthSummary.netChange()
                        .divide(currentMonthSummary.totalIncome(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                : null;
        String topCategory = currentMonthCategories.isEmpty() ? null : currentMonthCategories.getFirst().category();

        List<String> observations = buildObservations(currentMonthSummary, topCategory, currentAnomalies, savingsRatePercent);

        return new HealthSummary(totalBalance, currency, currentMonthSummary.totalIncome(), currentMonthSummary.totalExpense(),
                currentMonthSummary.netChange(), savingsRatePercent, topCategory, observations);
    }

    // --- fetch --------------------------------------------------------------------------------

    /** accountId null/blank = all accounts (existing behavior); otherwise just that one account. */
    private List<TransactionSummary> fetchTransactions(String sessionId, String accountId) {
        if (accountId != null && !accountId.isBlank()) {
            return transactionService.listTransactions(sessionId, accountId, null, null, null);
        }
        return fetchAllTransactions(sessionId);
    }

    private List<TransactionSummary> fetchAllTransactions(String sessionId) {
        List<ObieAccount> accounts = aisClient.getAccounts(sessionId).Data().Account();
        List<TransactionSummary> all = Concurrency.mapConcurrently(accounts,
                        account -> transactionService.listTransactions(sessionId, account.AccountId(), null, null, null))
                .stream()
                .flatMap(List::stream)
                .toList();
        log.debug("Insights fetched {} transactions across {} accounts", all.size(), accounts.size());
        return all;
    }

    // --- spending summary / category breakdown / trend ----------------------------------------

    private SpendingSummary computeSpendingSummary(List<TransactionSummary> all, YearMonth month) {
        List<TransactionSummary> monthly = filterByMonth(all, month);
        BigDecimal income = sumByIndicator(monthly, "Credit");
        BigDecimal expense = sumByIndicator(monthly, "Debit");
        return new SpendingSummary(month.toString(), resolveCurrency(all), income, expense, income.subtract(expense), monthly.size());
    }

    private List<CategoryBreakdown> computeCategoryBreakdown(List<TransactionSummary> transactions) {
        BigDecimal overallTotal = transactions.stream().map(TransactionSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, List<TransactionSummary>> byCategory = transactions.stream().collect(Collectors.groupingBy(TransactionSummary::category));

        return byCategory.entrySet().stream()
                .map(entry -> {
                    BigDecimal categoryTotal = entry.getValue().stream().map(TransactionSummary::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal percentage = overallTotal.signum() == 0
                            ? BigDecimal.ZERO
                            : categoryTotal.divide(overallTotal, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP);
                    return new CategoryBreakdown(entry.getKey(), categoryTotal, entry.getValue().size(), percentage);
                })
                .sorted(Comparator.comparing(CategoryBreakdown::totalAmount).reversed())
                .toList();
    }

    private List<MonthlyTrend> computeTrend(List<TransactionSummary> all, int months) {
        YearMonth currentMonth = YearMonth.now();
        YearMonth startMonth = currentMonth.minusMonths(Math.max(0, months - 1));
        List<MonthlyTrend> result = new ArrayList<>();
        for (YearMonth month = startMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            List<TransactionSummary> monthly = filterByMonth(all, month);
            BigDecimal income = sumByIndicator(monthly, "Credit");
            BigDecimal expense = sumByIndicator(monthly, "Debit");
            result.add(new MonthlyTrend(month.toString(), income, expense, income.subtract(expense), monthly.size()));
        }
        return result;
    }

    // --- anomalies ------------------------------------------------------------------------------

    private List<AnomalyTransaction> computeAnomalies(List<TransactionSummary> all) {
        Map<String, List<TransactionSummary>> byCategory = all.stream().collect(Collectors.groupingBy(TransactionSummary::category));
        List<AnomalyTransaction> anomalies = new ArrayList<>();

        for (List<TransactionSummary> categoryTransactions : byCategory.values()) {
            if (categoryTransactions.size() < MIN_TRANSACTIONS_FOR_ANOMALY_DETECTION) {
                continue;
            }
            double[] amounts = categoryTransactions.stream().mapToDouble(t -> t.amount().doubleValue()).toArray();
            double mean = mean(amounts);
            double stdDev = stdDev(amounts, mean);
            if (stdDev == 0) {
                continue;
            }
            BigDecimal meanBd = BigDecimal.valueOf(mean).setScale(2, RoundingMode.HALF_UP);
            BigDecimal stdDevBd = BigDecimal.valueOf(stdDev).setScale(2, RoundingMode.HALF_UP);
            double threshold = mean + ANOMALY_STD_DEV_MULTIPLIER.doubleValue() * stdDev;

            for (TransactionSummary transaction : categoryTransactions) {
                double amount = transaction.amount().doubleValue();
                if (amount > threshold) {
                    BigDecimal deviationMultiple = BigDecimal.valueOf((amount - mean) / stdDev).setScale(2, RoundingMode.HALF_UP);
                    anomalies.add(new AnomalyTransaction(transaction, meanBd, stdDevBd, deviationMultiple));
                }
            }
        }
        return anomalies.stream().sorted(Comparator.comparing(AnomalyTransaction::deviationMultiple).reversed()).toList();
    }

    // --- subscriptions --------------------------------------------------------------------------

    private List<SubscriptionCandidate> computeSubscriptions(List<TransactionSummary> all) {
        Map<String, List<TransactionSummary>> byMerchant = all.stream()
                .filter(t -> t.merchantName() != null && !t.merchantName().isBlank())
                .collect(Collectors.groupingBy(TransactionSummary::merchantName));

        List<SubscriptionCandidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<TransactionSummary>> entry : byMerchant.entrySet()) {
            List<TransactionSummary> transactions = entry.getValue().stream()
                    .sorted(Comparator.comparing(TransactionSummary::bookingDateTime))
                    .toList();
            if (transactions.size() < 2) {
                continue;
            }

            double averageAmount = transactions.stream().mapToDouble(t -> t.amount().doubleValue()).average().orElse(0);
            boolean amountsSimilar = transactions.stream()
                    .allMatch(t -> Math.abs(t.amount().doubleValue() - averageAmount) <= averageAmount * SUBSCRIPTION_AMOUNT_TOLERANCE);
            if (!amountsSimilar) {
                continue;
            }

            List<Double> intervalDays = new ArrayList<>();
            for (int i = 1; i < transactions.size(); i++) {
                Duration gap = Duration.between(transactions.get(i - 1).bookingDateTime(), transactions.get(i).bookingDateTime());
                intervalDays.add(gap.toHours() / 24.0);
            }
            double averageIntervalDays = intervalDays.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            if (averageIntervalDays < SUBSCRIPTION_MIN_INTERVAL_DAYS || averageIntervalDays > SUBSCRIPTION_MAX_INTERVAL_DAYS) {
                continue;
            }

            Instant lastBooking = transactions.getLast().bookingDateTime();
            candidates.add(new SubscriptionCandidate(
                    entry.getKey(),
                    BigDecimal.valueOf(averageAmount).setScale(2, RoundingMode.HALF_UP),
                    transactions.getFirst().currency(),
                    transactions.size(),
                    Math.round(averageIntervalDays * 10) / 10.0,
                    lastBooking));
        }
        return candidates.stream().sorted(Comparator.comparing(SubscriptionCandidate::lastBookingDateTime).reversed()).toList();
    }

    // --- health summary observations -------------------------------------------------------------

    private List<String> buildObservations(SpendingSummary monthSummary, String topCategory,
                                            List<AnomalyTransaction> anomalies, BigDecimal savingsRatePercent) {
        List<String> observations = new ArrayList<>();

        if (monthSummary.transactionCount() == 0) {
            observations.add("No transactions recorded yet this month.");
        } else if (monthSummary.netChange().signum() < 0) {
            observations.add("You spent more than you received this month.");
        } else {
            observations.add("You're in the green this month — income covered expenses.");
        }

        if (topCategory != null) {
            observations.add("Your top category this month is " + topCategory + ".");
        }

        if (savingsRatePercent != null) {
            observations.add("Savings rate this month: " + savingsRatePercent + "%.");
        }

        if (anomalies.isEmpty()) {
            observations.add("No unusual spending detected.");
        } else {
            observations.add(anomalies.size() + " unusual transaction(s) detected compared to their category's normal range.");
        }

        return observations;
    }

    // --- shared helpers ---------------------------------------------------------------------------

    private List<TransactionSummary> filterByMonth(List<TransactionSummary> transactions, YearMonth month) {
        return transactions.stream()
                .filter(t -> YearMonth.from(t.bookingDateTime().atZone(ZoneOffset.UTC)).equals(month))
                .toList();
    }

    private BigDecimal sumByIndicator(List<TransactionSummary> transactions, String indicator) {
        return transactions.stream()
                .filter(t -> indicator.equalsIgnoreCase(t.creditDebitIndicator()))
                .map(TransactionSummary::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String resolveCurrency(List<TransactionSummary> transactions) {
        return transactions.isEmpty() ? "GBP" : transactions.getFirst().currency();
    }

    private double mean(double[] values) {
        double sum = 0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private double stdDev(double[] values, double mean) {
        double sumSquares = 0;
        for (double value : values) {
            sumSquares += Math.pow(value - mean, 2);
        }
        return Math.sqrt(sumSquares / (values.length - 1));
    }
}
