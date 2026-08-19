package com.zhan.admin;

import com.zhan.admin.dto.AskTrendPoint;
import com.zhan.admin.dto.OverviewStats;
import com.zhan.common.ApiResponse;
import com.zhan.entity.AuditLog;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats/overview")
    public ApiResponse<OverviewStats> overview() {
        return ApiResponse.ok(adminService.overview());
    }

    @GetMapping("/stats/ask-trend")
    public ApiResponse<List<AskTrendPoint>> askTrend() {
        return ApiResponse.ok(adminService.askTrend());
    }

    @GetMapping("/audit-logs")
    public ApiResponse<Page<AuditLog>> auditLogs(@RequestParam(defaultValue = "0") int page,
                                                 @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(adminService.auditLogs(page, size));
    }
}
