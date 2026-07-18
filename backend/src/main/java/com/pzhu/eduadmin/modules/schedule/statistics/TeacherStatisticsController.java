package com.pzhu.eduadmin.modules.schedule.statistics;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherStatisticsController {

    private final TeacherStatisticsService teacherStatisticsService;

    @GetMapping("/statistics")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Map<String, Object>> overview() {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(teacherStatisticsService.getOverview(teacherId));
    }

    @GetMapping("/statistics/monthly")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Map<String, Object>>> monthly(
            @RequestParam(required = false) Integer year) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(teacherStatisticsService.getMonthly(teacherId, year));
    }
}
