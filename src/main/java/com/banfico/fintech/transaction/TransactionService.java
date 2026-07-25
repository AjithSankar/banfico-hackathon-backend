package com.banfico.fintech.transaction;

import com.banfico.fintech.sandbox.SandboxAisClient;
import com.banfico.fintech.sandbox.dto.ObieTransaction;
import com.banfico.fintech.sandbox.dto.ObieTransactionCreateRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

@Service
public class TransactionService {

    private final SandboxAisClient aisClient;
    private final TransactionCategoryClassifier classifier;

    public TransactionService(SandboxAisClient aisClient, TransactionCategoryClassifier classifier) {
        this.aisClient = aisClient;
        this.classifier = classifier;
    }

    /**
     * Fetches the full transaction history for one account (the sandbox has no server-side
     * filtering) and applies category/date filtering + sorting in this layer.
     */
    public List<TransactionSummary> listTransactions(String sessionId, String accountId, String category, LocalDate from, LocalDate to) {
        List<ObieTransaction> transactions = aisClient.getTransactions(sessionId, accountId).Data().Transaction();
        if (transactions == null) {
            return List.of();
        }
        return transactions.stream()
                .map(this::toSummary)
                .filter(t -> category == null || category.equalsIgnoreCase(t.category()))
                .filter(t -> from == null || !toUtcDate(t.bookingDateTime()).isBefore(from))
                .filter(t -> to == null || !toUtcDate(t.bookingDateTime()).isAfter(to))
                .sorted(Comparator.comparing(TransactionSummary::bookingDateTime).reversed())
                .toList();
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    public TransactionSummary createTransaction(String sessionId, String accountId, ObieTransactionCreateRequest request) {
        List<ObieTransaction> transactions = aisClient.createTransaction(sessionId, accountId, request).Data().Transaction();
        if (transactions == null || transactions.isEmpty()) {
            throw new IllegalStateException("Sandbox did not return the created transaction");
        }
        return toSummary(transactions.getFirst());
    }

    private TransactionSummary toSummary(ObieTransaction transaction) {
        return new TransactionSummary(
                transaction.TransactionId(),
                transaction.AccountId(),
                new BigDecimal(transaction.Amount().Amount()),
                transaction.Amount().Currency(),
                transaction.CreditDebitIndicator(),
                transaction.Status(),
                Instant.parse(transaction.BookingDateTime()),
                transaction.TransactionInformation(),
                transaction.MerchantDetails() == null ? null : transaction.MerchantDetails().MerchantName(),
                classifier.classify(transaction));
    }

    private LocalDate toUtcDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }
}
