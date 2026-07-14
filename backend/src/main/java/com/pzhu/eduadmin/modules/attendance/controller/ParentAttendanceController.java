package com.pzhu.eduadmin.modules.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentAttendanceController {

    private final AttendanceService attendanceService;
    private final ParentStudentMapper parentStudentMapper;

    @GetMapping("/attendances")
    @Deprecated
    public Result<PageResult<Attendance>> myChildAttendances(PageQuery query,
                                                              @RequestParam(defaultValue = "0") Long studentId) {
        return childAttendancesByStudentId(query, studentId);
    }

    @GetMapping("/students/{studentId}/attendance")
    public Result<PageResult<Attendance>> childAttendancesByStudentId(PageQuery query,
                                                                       @PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        if (studentId == 0) {
            ParentStudent binding = parentStudentMapper.selectOne(
                    new LambdaQueryWrapper<ParentStudent>().eq(ParentStudent::getParentUserId, parentUserId)
                            .orderByAsc(ParentStudent::getId).last("LIMIT 1"));
            if (binding == null) {
                throw new BusinessException(403, "暂无绑定的学员，请联系教务绑定");
            }
            studentId = binding.getStudentId();
        } else {
            checkParentBinding(parentUserId, studentId);
        }
        return Result.success(PageResult.of(attendanceService.pageByStudentId(studentId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/students/{studentId}/schedule")
    public Result<List<ScheduleLesson>> childSchedule(@PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(attendanceService.getStudentSchedules(studentId));
    }

    private void checkParentBinding(Long parentUserId, Long studentId) {
        Long count = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId)
                        .eq(ParentStudent::getStudentId, studentId));
        if (count == 0) {
            throw new BusinessException(403, "无权访问该学员数据");
        }
    }
}
