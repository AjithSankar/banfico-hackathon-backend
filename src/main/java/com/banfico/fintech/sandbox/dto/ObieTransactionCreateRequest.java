package com.banfico.fintech.sandbox.dto;

import java.util.List;

/**
 * Request body for POST /accounts/{id}/transactions — exact shape from the Postman
 * collection (demo-seeding, bonus).
 */
public record ObieTransactionCreateRequest(
        String TransactionReference,
        ObieAmount Amount,
        String CreditDebitIndicator,
        String Status,
        String BookingDateTime,
        String ValueDateTime,
        String TransactionInformation,
        ObieBankTransactionCode BankTransactionCode,
        ObieProprietaryBankTransactionCode ProprietaryBankTransactionCode,
        List<ObieExtendedProprietaryCode> ExtendedProprietaryBankTransactionCodes,
        ObieTransactionBalance Balance,
        String PaymentPurposeCode,
        ObieMerchantDetails MerchantDetails,
        ObieAgent CreditorAgent,
        ObieAccountRef CreditorAccount,
        ObieAgent DebtorAgent,
        ObieAccountRef DebtorAccount,
        ObieUltimateParty UltimateCreditor,
        ObieUltimateParty UltimateDebtor,
        ObieCardInstrument CardInstrument) {
}
