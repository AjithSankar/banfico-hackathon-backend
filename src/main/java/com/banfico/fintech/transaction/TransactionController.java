package com.banfico.fintech.transaction;

import com.banfico.fintech.auth.CurrentSession;
import com.banfico.fintech.common.ApiResponse;
import com.banfico.fintech.common.PagedResult;
import com.banfico.fintech.sandbox.dto.ObieTransactionCreateRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/accounts/{accountId}/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public ApiResponse<PagedResult<TransactionSummary>> listTransactions(
            @PathVariable String accountId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        List<TransactionSummary> all = transactionService.listTransactions(CurrentSession.sessionId(), accountId, category, from, to);
        return ApiResponse.ok(paginate(all, page, size));
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    @PostMapping
    public ApiResponse<TransactionSummary> createTransaction(@PathVariable String accountId, @RequestBody ObieTransactionCreateRequest request) {
        return ApiResponse.ok(transactionService.createTransaction(CurrentSession.sessionId(), accountId, request));
    }

    private PagedResult<TransactionSummary> paginate(List<TransactionSummary> all, int page, int size) {
        int fromIndex = Math.min(page * size, all.size());
        int toIndex = Math.min(fromIndex + size, all.size());
        return new PagedResult<>(all.subList(fromIndex, toIndex), page, size, all.size());
    }
}
