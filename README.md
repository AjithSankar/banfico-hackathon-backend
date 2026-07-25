We built SmartFinSights AI, an AI-powered personal finance assistant that connects to real Open Banking APIs to provide live account information, spending insights, and personalized financial recommendations.

# High Level Architecture

                    ┌──────────────────────────────┐
                    │      React Frontend          │
                    │------------------------------│
                    │ Login                        │
                    │ Dashboard                    │
                    │ Accounts                     │
                    │ Transactions                 │
                    │ Spending Insights            │
                    │ AI Assistant                 │
                    │ Recommendations              │
                    └──────────────┬───────────────┘
                                   │
                        REST APIs (JWT Session)
                                   │
                    ┌──────────────▼───────────────┐
                    │      Spring Boot API         │
                    │------------------------------│
                    │ Authentication               │
                    │ Dashboard APIs               │
                    │ Accounts APIs                │
                    │ Transactions APIs            │
                    │ Insights Engine              │
                    │ AI Service                   │
                    └──────────────┬───────────────┘
                     ┌─────────────┴─────────────┐
                     │                           │
          ┌──────────▼──────────┐     ┌──────────▼──────────┐
          │ Open Banking Sandbox │     │ Local AI (Ollama)   │
          │ Accounts             │     │ Spring AI           │
          │ Balances             │     │ Tool Calling        │
          │ Transactions         │     │ Financial Assistant │
          └──────────────────────┘     └─────────────────────┘


# Backend Architecture




