package com.pzhu.eduadmin.modules.statistics.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;
import com.pzhu.eduadmin.modules.statistics.service.StatisticsService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/statistics")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<StatisticsSnapshot>> listSnapshots(PageQuery query) {
        return Result.success(PageResult.of(statisticsService.pageSnapshots((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/organization")
    public Result<Organization> getOrganization() {
        return Result.success(statisticsService.getOrganization());
    }

    @PutMapping("/organization")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Organization> updateOrganization(@RequestBody Organization organization) {
        return Result.success(statisticsService.updateOrganization(organization));
    }

    @GetMapping("/logs")
    @RequireRole({"SUPER_ADMIN"})
    @Deprecated
    public Result<PageResult<OperationLog>> listLogs(PageQuery query) {
        return listOperationLogs(query);
    }

    @GetMapping("/operation-logs")
    @RequireRole({"SUPER_ADMIN"})
    public Result<PageResult<OperationLog>> listOperationLogs(PageQuery query) {
        return Result.success(PageResult.of(statisticsService.pageOperationLogs((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/dashboard")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Map<String, Object>> dashboard() {
        return Result.success(statisticsService.getDashboard());
    }

    @GetMapping("/statistics/enrollments")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Map<String, Object>> enrollmentStats() {
        Map<String, Object> dashboard = statisticsService.getDashboard();
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        return Result.success(Map.of("activeStudents", cards.get("activeStudents")));
    }

    @GetMapping("/statistics/attendance-rate")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Map<String, Object>> attendanceRateStats() {
        Map<String, Object> dashboard = statisticsService.getDashboard();
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        return Result.success(Map.of("attendanceRate", cards.get("attendanceRate")));
    }

    @GetMapping("/statistics/lesson-consumption")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Map<String, Object>> lessonConsumptionStats() {
        Map<String, Object> dashboard = statisticsService.getDashboard();
        Map<String, Object> charts = (Map<String, Object>) dashboard.get("charts");
        return Result.success(Map.of("lessonTrend", charts.get("lessonTrend")));
    }

    @GetMapping("/statistics/revenue")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<Map<String, Object>> revenueStats() {
        Map<String, Object> dashboard = statisticsService.getDashboard();
        Map<String, Object> charts = (Map<String, Object>) dashboard.get("charts");
        return Result.success(Map.of("revenueTrend", charts.get("revenueTrend")));
    }

    @GetMapping("/statistics/teacher-workload")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Object> teacherWorkload(@RequestParam(defaultValue = "") String month) {
        return Result.success(statisticsService.getTeacherWorkload(month));
    }

    @GetMapping("/statistics/student-loss")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Object> studentLoss() {
        return Result.success(statisticsService.getStudentLossTrend());
    }
}
