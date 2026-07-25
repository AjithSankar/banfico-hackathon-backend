package com.banfico.fintech.account;

import com.banfico.fintech.auth.CurrentSession;
import com.banfico.fintech.common.ApiResponse;
import com.banfico.fintech.sandbox.dto.ObieAccountCreateRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public ApiResponse<List<AccountSummary>> listAccounts() {
        return ApiResponse.ok(accountService.listAccounts(CurrentSession.sessionId()));
    }

    @GetMapping("/{accountId}")
    public ApiResponse<AccountDetail> getAccount(@PathVariable String accountId) {
        return ApiResponse.ok(accountService.getAccount(CurrentSession.sessionId(), accountId));
    }

    @GetMapping("/{accountId}/balance")
    public ApiResponse<BalanceResponse> getBalance(@PathVariable String accountId) {
        return ApiResponse.ok(accountService.getBalance(CurrentSession.sessionId(), accountId));
    }

    /** Demo-seeding only (bonus) — not a core end-user-facing feature. */
    @PostMapping
    public ApiResponse<AccountSummary> createAccount(@RequestBody ObieAccountCreateRequest request) {
        return ApiResponse.ok(accountService.createAccount(CurrentSession.sessionId(), request));
    }
}
