package dev.ak.nexusficore.controller;

import dev.ak.nexusficore.config.ChatToolExecutionTracker;
import dev.ak.nexusficore.dto.ChatRequestDto;
import dev.ak.nexusficore.dto.ChatResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final ChatClient chatClient;
    private final ChatToolExecutionTracker toolExecutionTracker;

    public ChatController(ChatClient chatClient, ChatToolExecutionTracker toolExecutionTracker) {
        this.chatClient = chatClient;
        this.toolExecutionTracker = toolExecutionTracker;
    }

    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request) {
        String reply = chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, request.sessionId()))
                .user(request.message())
                .call()
                .content();

        boolean subscriptionsChanged = toolExecutionTracker.isSubscriptionChanged();
        boolean autopilotChanged = toolExecutionTracker.isAutopilotChanged();

        return new ChatResponseDto(
                reply,
                subscriptionsChanged || autopilotChanged,
                subscriptionsChanged,
                autopilotChanged
        );
    }
}
