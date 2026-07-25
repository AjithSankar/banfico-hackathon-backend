package com.banfico.fintech.ai;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Spring AI ChatClient against the auto-configured Ollama ChatModel (see Phase 0:
 * local Ollama, no API key). Conversation memory is in-memory only, windowed to the last 20
 * messages per conversationId — no persistence, cleared on app restart.
 *
 * <p>The MessageChatMemoryAdvisor is deliberately NOT registered as a default advisor here — it
 * requires a conversationId on every call it wraps, but coachingTip()/recommendations() in
 * FinancialAssistantService are single-shot stateless prompts with no conversation to attach to.
 * Registering it as a default previously broke both of those with
 * "IllegalArgumentException: conversationId cannot be null". FinancialAssistantService.chat()
 * adds the memory advisor per-call instead, where a conversationId genuinely exists.
 */
@Configuration
public class ChatClientConfig {

    private static final String SYSTEM_PROMPT = """
            You are a financial assistant embedded in a banking app. Answer the user's questions
            about their accounts, balances, transactions, spending, and anomalies using the tools
            provided to you — never invent numbers. If a tool call fails or returns no data, say
            so plainly rather than guessing. Keep answers concise (a few sentences), use the
            currency shown in the tool data, and ground every answer in the tool results rather
            than giving generic financial advice unrelated to the user's actual data.
            """;

    @Bean
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .chatMemoryRepository(new InMemoryChatMemoryRepository())
                .maxMessages(20)
                .build();
    }

    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel)
                .defaultSystem(SYSTEM_PROMPT)
                .build();
    }
}
