package com.pzhu.eduadmin.modules.attendance.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.attendance.service.LeaveRequestService;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherAttendanceController {

    private final AttendanceService attendanceService;
    private final ScheduleService scheduleService;
    private final LeaveRequestService leaveRequestService;

    @GetMapping({"/lessons", "/schedules"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult> myLessons(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(attendanceService.pageTeacherLessons(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/today-lessons")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<ScheduleLesson>> todayLessons() {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(attendanceService.getTodayLessons(teacherId));
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

    // ========== 请假审批 ==========

    @GetMapping("/leave-requests")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<LeaveRequest>> myLeaveRequests(PageQuery query) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(leaveRequestService.pageByTeacher(teacherId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PutMapping("/leave-requests/{id}/audit")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<LeaveRequest> auditLeaveRequest(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        Integer status = (Integer) body.get("status");
        if (status == null || (status != 2 && status != 3)) {
            throw new com.pzhu.eduadmin.common.BusinessException(400, "审核状态只能为 2(通过) 或 3(驳回)");
        }
        String remark = (String) body.getOrDefault("remark", "");
        return Result.success(leaveRequestService.auditByTeacher(id, status,
                CurrentUserHolder.get().getUserId(), remark));
    }
}
