# SmartFinSights AI -- Architecture & Presentation Speaker Notes

## Application Introduction

We have built SmartFinSights AI, an AI-powered personal finance platform built during this hackathon.

Our application combines Core Banking, Artificial Intelligence, and Modern Web Technologies to help users better understand and manage their finances.

Instead of simply displaying account balances and transaction history, our platform analyzes a customer's financial data and provides meaningful insights, personalized recommendations, and a conversational AI assistant capable of answering questions about their finances.

One of the key highlights of our solution is that it works with live Open Banking APIs, meaning every insight and AI response is generated using real banking data rather than static mock data.

## Our solution consists of five major capabilities: 

- The first is a Unified Banking Dashboard, where users can view all of their accounts, balances, and recent transactions in one place.

- The second capability is Financial Insights, where we analyze transaction history to generate spending summaries, category analysis, monthly spending trends, financial health indicators, recurring subscriptions, and anomaly detection.

- The third feature is our AI Financial Assistant. Users can ask natural language questions such as:

  "How much did I spend on dining this month?"

  or

    "What are my biggest expenses?"

- The AI understands the question, retrieves the user's actual banking information, and provides personalized answers.

Finally, we provide AI-powered Financial Recommendations, helping users identify opportunities to save money and improve their financial habits.

# Tech Stacks:

To build this solution, we used modern technologies across the frontend, backend, AI, and banking integration layers.

For the frontend, we used React 19, Vite, and Tailwind CSS to create a responsive and modern user interface.

For the backend, we chose Java 25 and Spring Boot 4, which provide a robust and scalable framework for building RESTful services.

To integrate Artificial Intelligence, we used Spring AI together with a locally hosted Ollama Large Language Model.

For resilience and reliability, we used Resilience4j to handle transient failures while communicating with external services.

 Finally, our application integrates with Core Banking APIs, allowing us to securely retrieve live account, balance, and transaction data.

# AI ASSISTANT:

One of the most exciting features of our application is the AI Financial Assistant.

Rather than relying solely on the language model's knowledge, we implemented Spring AI Tool Calling.

When a user asks a financial question, Spring AI identifies which backend service can answer that question.

The backend retrieves live banking information and passes it back to the language model as context.

The model then generates a natural language response based on the user's actual financial data.

This approach significantly reduces hallucinations and ensures the AI provides accurate and personalized financial guidance.

------------------------------------------------------------------------

# High-Level Architecture


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
                                REST APIs 
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


-   The React frontend provides a responsive user experience.
-   Spring Boot exposes secure REST APIs and contains the business
    logic.
-   Open Banking APIs provide live account, balance, and transaction
    data.
-   Spring AI integrates with Ollama to provide conversational financial
    assistance.
-   AI responses are grounded using live banking data rather than
    generic model knowledge.

------------------------------------------------------------------------

# Frontend Architecture

``` text
AppShell
├── Sidebar
├── Topbar
├── AI Chat Widget
└── React Router
    ├── Dashboard
    ├── Accounts
    ├── Transactions
    ├── Insights
    └── Recommendations

Axios API Layer
        │
Spring Boot APIs
```

**Highlights**

-   React 19 + Vite
-   Tailwind CSS
-   Lazy-loaded routes
-   Centralized Axios client
-   Persistent AppShell for smooth navigation

------------------------------------------------------------------------

# Backend Architecture

``` text
Controllers
     │
Services
     │
Business Logic
     │
Integration Layer
 ┌───┴────────────┐
 │                │
Open Banking   Spring AI
```

**Highlights**

-   Java 25
-   Spring Boot
-   Spring AI
-   Resilience4j
-   Layered architecture
-   Clear separation of concerns

------------------------------------------------------------------------

# Authentication Flow

``` text
User Login
    │
React Login
    │
POST /api/auth/login
    │
Spring Boot
    │
OAuth/Open Banking
    │
Session Token
    │
Frontend
```

**Key Points**

-   Real banking access tokens remain inside the backend.
-   The frontend receives only an opaque session token.
-   Token refresh is handled transparently.

------------------------------------------------------------------------

# Banking Request Flow

``` text
Dashboard Request
      │
Dashboard Controller
      │
Dashboard Service
      │
Sandbox Client
      │
Open Banking APIs
      │
Accounts + Balances + Transactions
      │
Aggregated Response
      │
Frontend
```

------------------------------------------------------------------------

# AI Flow

``` text
User
 │
AI Chat Widget
 │
Spring AI
 │
Financial Tools
 │
Account & Insights Services
 │
Open Banking APIs
 │
Grounded Response
```

