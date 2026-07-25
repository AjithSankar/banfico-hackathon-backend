package com.banfico.fintech.dashboard;

import com.banfico.fintech.auth.CurrentSession;
import com.banfico.fintech.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ApiResponse<DashboardResponse> dashboard() {
        return ApiResponse.ok(dashboardService.buildDashboard(CurrentSession.sessionId()));
    }
}
