package com.pzhu.eduadmin.modules.statistics.controller;

import com.pzhu.eduadmin.modules.statistics.service.ExportService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/export")
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/payments")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public void exportPayments(HttpServletResponse response,
                               @RequestParam(required = false) String startDate,
                               @RequestParam(required = false) String endDate) throws IOException {
        exportService.exportPayments(response, startDate, endDate);
    }

    @GetMapping("/lesson-flows")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public void exportLessonFlows(HttpServletResponse response,
                                  @RequestParam(required = false) String startDate,
                                  @RequestParam(required = false) String endDate) throws IOException {
        exportService.exportLessonFlows(response, startDate, endDate);
    }

    @GetMapping("/salaries")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public void exportSalaries(HttpServletResponse response,
                               @RequestParam(required = false) String month) throws IOException {
        exportService.exportSalaries(response, month);
    }
}
