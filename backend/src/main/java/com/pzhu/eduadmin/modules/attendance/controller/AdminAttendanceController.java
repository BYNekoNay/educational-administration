package com.pzhu.eduadmin.modules.attendance.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu")
@RequiredArgsConstructor
public class AdminAttendanceController {

    private final AttendanceService attendanceService;

    @GetMapping("/attendances")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Attendance>> list(PageQuery query) {
        return Result.success(PageResult.of(attendanceService.page((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/attendances/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Attendance> get(@PathVariable Long id) {
        Attendance attendance = attendanceService.getById(id);
        if (attendance == null) {
            throw new BusinessException(404, "考勤记录不存在");
        }
        return Result.success(attendance);
    }

    @PostMapping("/attendances")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Attendance> submit(@RequestBody Attendance attendance) {
        // Bug3 fix: 防止客户端传入已有id/时间戳（mass assignment保护）
        attendance.setId(null);
        attendance.setCreateTime(null);
        attendance.setUpdateTime(null);
        return Result.success(attendanceService.submit(attendance));
    }
}
