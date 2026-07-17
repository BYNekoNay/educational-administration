package com.pzhu.eduadmin.modules.attendance.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherAttendanceController {

    private final AttendanceService attendanceService;
    private final ScheduleService scheduleService;

    @GetMapping({"/lessons", "/schedules"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult> myLessons(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(attendanceService.pageTeacherLessons(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/lessons/{lessonId}/students")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<ClassStudent>> lessonStudents(@PathVariable Long lessonId) {
        attendanceService.checkTeacherLessonOwnership(lessonId);
        return Result.success(attendanceService.getLessonStudents(lessonId));
    }

    @GetMapping("/lessons/{lessonId}/attendances")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Attendance>> lessonAttendances(@PathVariable Long lessonId) {
        attendanceService.checkTeacherLessonOwnership(lessonId);
        return Result.success(attendanceService.getByLessonId(lessonId));
    }

    @PostMapping("/lessons/{lessonId}/attendances")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Attendance>> batchSubmit(@PathVariable Long lessonId,
                                                 @RequestBody List<Attendance> list) {
        return Result.success(attendanceService.batchSubmit(lessonId, list));
    }

    @GetMapping("/adjust-requests")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<ScheduleAdjustRequest>> myAdjustRequests(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(scheduleService.pageTeacherAdjustRequests(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping({"/schedules/{lessonId}/adjust-requests", "/lessons/{lessonId}/adjust-requests"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleAdjustRequest> createMyAdjustRequest(@PathVariable Long lessonId,
                                                                @RequestBody ScheduleAdjustRequest request) {
        request.setLessonId(lessonId);
        request.setApplicantId(CurrentUserHolder.get().getUserId());
        request.setStatus(1);
        return Result.success(scheduleService.createAdjustRequest(request));
    }
}
