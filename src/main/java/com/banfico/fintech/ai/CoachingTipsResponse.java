package com.banfico.fintech.ai;

import java.util.List;

/** Structured output shape the model is asked to return; also used for the rule-based fallback. */
public record CoachingTipsResponse(List<String> tips) {
}
