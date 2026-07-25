package com.banfico.fintech.ai;

import com.banfico.fintech.auth.CurrentSession;
import com.banfico.fintech.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class ChatController {

    private final FinancialAssistantService assistantService;

    public ChatController(FinancialAssistantService assistantService) {
        this.assistantService = assistantService;
    }

    @PostMapping("/chat")
    public ApiResponse<ChatResponse> chat(@Valid @RequestBody ChatRequest request) {
        String sessionId = CurrentSession.sessionId();
        return ApiResponse.ok(assistantService.chat(sessionId, request.message(), request.conversationId()));
    }

    @GetMapping("/coaching-tip")
    public ApiResponse<CoachingTipsResponse> coachingTip() {
        return ApiResponse.ok(assistantService.coachingTip(CurrentSession.sessionId()));
    }

    @GetMapping("/recommendations")
    public ApiResponse<RecommendationsResponse> recommendations() {
        return ApiResponse.ok(assistantService.recommendations(CurrentSession.sessionId()));
    }
}
