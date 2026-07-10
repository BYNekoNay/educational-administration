package com.pzhu.eduadmin.modules.attendance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;
    private final ParentStudentMapper parentStudentMapper;
    private final ScheduleService scheduleService;

    // ---- 教务端 ----

    @GetMapping("/edu/attendances")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Attendance>> list(PageQuery query) {
        return Result.success(PageResult.of(attendanceService.page((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/edu/attendances/{id}")
    public Result<Attendance> get(@PathVariable Long id) {
        return Result.success(attendanceService.getById(id));
    }

    @PostMapping("/edu/attendances")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Attendance> submit(@RequestBody Attendance attendance) {
        return Result.success(attendanceService.submit(attendance));
    }

    // ---- 教师端 ----

    @GetMapping({"/teacher/lessons", "/teacher/schedules"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult> myLessons(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(attendanceService.pageTeacherLessons(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/teacher/lessons/{lessonId}/students")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<ClassStudent>> lessonStudents(@PathVariable Long lessonId) {
        return Result.success(attendanceService.getLessonStudents(lessonId));
    }

    @GetMapping("/teacher/lessons/{lessonId}/attendances")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Attendance>> lessonAttendances(@PathVariable Long lessonId) {
        return Result.success(attendanceService.getByLessonId(lessonId));
    }

    @PostMapping("/teacher/lessons/{lessonId}/attendances")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Attendance>> batchSubmit(@PathVariable Long lessonId,
                                                 @RequestBody List<Attendance> list) {
        return Result.success(attendanceService.batchSubmit(lessonId, list));
    }

    // ---- 教师调课申请 ----

    @GetMapping("/teacher/adjust-requests")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<ScheduleAdjustRequest>> myAdjustRequests(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(scheduleService.pageTeacherAdjustRequests(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping({"/teacher/schedules/{lessonId}/adjust-requests", "/teacher/lessons/{lessonId}/adjust-requests"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleAdjustRequest> createMyAdjustRequest(@PathVariable Long lessonId,
                                                                @RequestBody ScheduleAdjustRequest request) {
        request.setLessonId(lessonId);
        request.setApplicantId(CurrentUserHolder.get().getUserId());
        request.setStatus(1);
        return Result.success(scheduleService.createAdjustRequest(request));
    }

    // ---- 家长端 ----

    @GetMapping("/parent/attendances")
    @Deprecated
    public Result<PageResult<Attendance>> myChildAttendances(PageQuery query,
                                                              @RequestParam(defaultValue = "0") Long studentId) {
        return childAttendancesByStudentId(query, studentId);
    }

    @GetMapping("/parent/students/{studentId}/attendance")
    public Result<PageResult<Attendance>> childAttendancesByStudentId(PageQuery query,
                                                                       @PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        if (studentId == 0) {
            // 默认取第一个绑定的学员
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

    @GetMapping("/parent/students/{studentId}/schedule")
    public Result<List<ScheduleLesson>> childSchedule(@PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(attendanceService.getStudentSchedules(studentId));
    }

    /**
     * 校验家长是否与学员存在绑定关系（行级数据隔离）
     */
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
