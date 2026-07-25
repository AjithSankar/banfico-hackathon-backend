package com.banfico.fintech.insights;

import java.util.List;

public record CategoryBreakdownResponse(String month, String currency, List<CategoryBreakdown> categories) {
}
