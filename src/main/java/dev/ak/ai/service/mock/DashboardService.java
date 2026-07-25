package dev.ak.ai.service.mock;


import dev.ak.ai.dto.mock.Account;
import dev.ak.ai.dto.mock.DashboardResponse;
import dev.ak.ai.dto.mock.Transaction;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class DashboardService {

    public DashboardResponse getDashboardData() {
        // Mocking Accounts
        List<Account> accounts = List.of(
            new Account("ACC-001", "Current", "****1234", 12500.50, "INR"),
            new Account("ACC-002", "Savings", "****5678", 85000.00, "INR"),
            new Account("ACC-003", "Credit Card", "****9012", -3200.00, "INR") // Negative balance for credit owed
        );

        // Mocking Transactions (Seeded with interesting data for future AI analysis)
        List<Transaction> transactions = List.of(
            new Transaction("TXN-101", "ACC-001", LocalDate.now().minusDays(1).toString(), 899.00, "DEBIT", "Netflix", "Subscription"),
            new Transaction("TXN-102", "ACC-001", LocalDate.now().minusDays(2).toString(), 5000.00, "DEBIT", "Zomato", "Dining"),
            new Transaction("TXN-103", "ACC-002", LocalDate.now().minusDays(3).toString(), 150000.00, "CREDIT", "TechCorp Inc.", "Salary"),
            new Transaction("TXN-104", "ACC-003", LocalDate.now().minusDays(4).toString(), 3500.00, "DEBIT", "Amazon", "Shopping"),
            new Transaction("TXN-105", "ACC-001", LocalDate.now().minusDays(5).toString(), 1999.00, "DEBIT", "Gym", "Subscription"),
            new Transaction("TXN-106", "ACC-001", LocalDate.now().minusDays(6).toString(), 2000.00, "DEBIT", "Uber", "Transport"),
            new Transaction("TXN-107", "ACC-001", LocalDate.now().minusDays(4).toString(), 6000.00, "DEBIT", "Flipkart", "Shopping")

        );

        // Calculate total net worth
        double netWorth = accounts.stream().mapToDouble(Account::balance).sum();

        return new DashboardResponse("Ajithkumar Sankar", netWorth, accounts, transactions);
    }
}