package com.banfico.fintech.ai;

/**
 * A single personalized recommendation. {@code priority} is one of "high"/"medium"/"low";
 * {@code category} is nullable (some recommendations, e.g. a savings goal, aren't tied to one
 * spending category).
 */
public record Recommendation(String title, String description, String category, String priority) {
}
