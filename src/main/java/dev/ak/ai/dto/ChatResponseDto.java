package dev.ak.nexusficore.dto;

public record ChatResponseDto(String reply,
                       boolean workflowChanged,
                       boolean subscriptionsChanged,
                       boolean autopilotChanged) {
}