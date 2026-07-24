package dev.ak.ai.controller;


import dev.ak.ai.dto.ChatRequestDto;
import dev.ak.ai.dto.ChatResponseDto;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@CrossOrigin(origins = "http://localhost:5173")
public class ChatController {

    private final ChatClient chatClient;

    public ChatController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostMapping
    public ChatResponseDto chat(@RequestBody ChatRequestDto request) {
        String response = chatClient.prompt()
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, request.sessionId()))
                .user(request.message())
                .call()
                .content();

        return new ChatResponseDto(response);

    }
}
