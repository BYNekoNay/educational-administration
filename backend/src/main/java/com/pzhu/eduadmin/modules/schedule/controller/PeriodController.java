package com.pzhu.eduadmin.modules.schedule.controller;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.schedule.entity.Period;
import com.pzhu.eduadmin.modules.schedule.service.PeriodService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/edu")
@RequiredArgsConstructor
public class PeriodController {

    private final PeriodService periodService;

    @GetMapping("/periods")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "TEACHER", "PARENT"})
    public Result<java.util.List<Period>> list() {
        return Result.success(periodService.listAll());
    }

    @GetMapping("/periods/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Period> get(@PathVariable Long id) {
        return Result.success(periodService.getById(id));
    }

    @PostMapping("/periods")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Period> create(@Valid @RequestBody Period period) {
        return Result.success(periodService.create(period));
    }

    @PutMapping("/periods/{id}")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Period> update(@PathVariable Long id, @Valid @RequestBody Period period) {
        return Result.success(periodService.update(id, period));
    }

    @DeleteMapping("/periods/{id}")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        periodService.delete(id);
        return Result.success();
    }

    @PostMapping("/schedules/migrate-periods")
    @RequireRole({"SUPER_ADMIN"})
    public Result<Map<String, Object>> migratePeriods() {
        return Result.success(periodService.migrateExistingLessons());
    }
}
