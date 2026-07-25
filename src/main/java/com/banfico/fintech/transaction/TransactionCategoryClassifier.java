package com.banfico.fintech.transaction;

import com.banfico.fintech.sandbox.dto.ObieTransaction;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * The sandbox has no clean "category" field, so we classify from MerchantCategoryCode (MCC)
 * first, falling back to keyword-matching on the transaction description/merchant name.
 */
@Component
public class TransactionCategoryClassifier {

    private static final Map<String, String> MCC_CATEGORIES = Map.ofEntries(
            Map.entry("5411", "Groceries"),
            Map.entry("5412", "Groceries"),
            Map.entry("5422", "Groceries"),
            Map.entry("5812", "Dining"),
            Map.entry("5813", "Dining"),
            Map.entry("5814", "Dining"),
            Map.entry("4900", "Utilities"),
            Map.entry("4899", "Utilities"),
            Map.entry("1711", "Home Services"),
            Map.entry("5999", "Shopping"),
            Map.entry("5311", "Shopping"),
            Map.entry("5651", "Shopping"),
            Map.entry("4111", "Transport"),
            Map.entry("4121", "Transport"),
            Map.entry("5541", "Transport"),
            Map.entry("5542", "Transport"),
            Map.entry("7841", "Entertainment"),
            Map.entry("5815", "Subscriptions"),
            Map.entry("5968", "Subscriptions"),
            Map.entry("6011", "Cash Withdrawal"),
            Map.entry("5874", "Transfers"));

    private static final List<Map.Entry<String, String>> KEYWORD_CATEGORIES = List.of(
            Map.entry("rent", "Housing"),
            Map.entry("mortgage", "Housing"),
            Map.entry("gas bill", "Utilities"),
            Map.entry("utility", "Utilities"),
            Map.entry("utilities", "Utilities"),
            Map.entry("electric", "Utilities"),
            Map.entry("grocery", "Groceries"),
            Map.entry("groceries", "Groceries"),
            Map.entry("supermarket", "Groceries"),
            Map.entry("restaurant", "Dining"),
            Map.entry("subscription", "Subscriptions"),
            Map.entry("netflix", "Subscriptions"),
            Map.entry("spotify", "Subscriptions"),
            Map.entry("transfer", "Transfers"),
            Map.entry("salary", "Income"),
            Map.entry("payroll", "Income"),
            Map.entry("cash from", "Transfers"));

    public String classify(ObieTransaction transaction) {
        String mcc = transaction.MerchantDetails() == null ? null : transaction.MerchantDetails().MerchantCategoryCode();
        if (mcc != null && MCC_CATEGORIES.containsKey(mcc)) {
            return MCC_CATEGORIES.get(mcc);
        }

        String description = transaction.TransactionInformation() == null ? "" : transaction.TransactionInformation();
        String merchantName = transaction.MerchantDetails() == null || transaction.MerchantDetails().MerchantName() == null
                ? "" : transaction.MerchantDetails().MerchantName();
        String haystack = (description + " " + merchantName).toLowerCase();

        for (Map.Entry<String, String> entry : KEYWORD_CATEGORIES) {
            if (haystack.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "Other";
    }
}
