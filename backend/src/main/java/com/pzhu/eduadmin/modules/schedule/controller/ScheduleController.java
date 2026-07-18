package com.pzhu.eduadmin.modules.schedule.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleService;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.UserService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/edu")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final UserMapper userMapper;
    private final UserService userService;
    private final NotificationService notificationService;

    // ========== 教师下拉列表 ==========

    @GetMapping("/teachers")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<User>> listTeachers() {
        List<User> teachers = userMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "TEACHER")
                        .eq(User::getStatus, 1));
        // 清除密码字段
        teachers.forEach(t -> t.setPassword(null));
        // 填充教学特长
        userService.fillTeachersSpecialties(teachers);
        return Result.success(teachers);
    }

    // ========== 课次 ==========

    @GetMapping("/schedules")
    public Result<PageResult<ScheduleLesson>> listLessons(
            PageQuery query,
            @RequestParam(required = false) Long courseId,
            @RequestParam(required = false) Long classId,
            @RequestParam(required = false) Long teacherId,
            @RequestParam(required = false) Long classroomId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return Result.success(PageResult.of(scheduleService.pageScheduleLessons(
                (int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder(),
                courseId, classId, teacherId, classroomId, status, dateFrom, dateTo)));
    }

    /** 教师端：查看自己的课表（复用排课接口，自动取 teacherId） */
    @GetMapping("/teacher/schedules")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<ScheduleLesson>> teacherSchedules(
            PageQuery query,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        Long teacherId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(scheduleService.pageScheduleLessons(
                (int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder(),
                null, null, teacherId, null, null, dateFrom, dateTo)));
    }

    @GetMapping("/schedules/{id}")
    public Result<ScheduleLesson> getLesson(@PathVariable Long id) {
        return Result.success(scheduleService.getLessonById(id));
    }

    @PostMapping("/schedules/check-conflict")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Map<String, Object>> checkConflict(@RequestBody ScheduleLesson lesson) {
        List<String> conflicts = scheduleService.checkConflict(lesson);
        return Result.success(Map.of("conflicts", conflicts, "hasConflict", !conflicts.isEmpty()));
    }

    @PostMapping("/schedules/batch")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> batchCreate(@RequestBody List<ScheduleLesson> lessons) {
        if (lessons == null || lessons.isEmpty()) {
            throw new BusinessException(400, "排课列表不能为空");
        }
        scheduleService.batchCreate(lessons);
        return Result.success();
    }

    @PostMapping("/schedules")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleLesson> createLesson(@Valid @RequestBody ScheduleLesson lesson) {
        List<String> conflicts = scheduleService.checkConflict(lesson);
        if (!conflicts.isEmpty()) {
            return Result.fail(409, String.join("；", conflicts));
        }
        return Result.success(scheduleService.createLesson(lesson));
    }

    @PutMapping(value = {"/schedules/{id}", "/schedules/{id}/adjust"})
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleLesson> updateLesson(@PathVariable Long id, @RequestBody ScheduleLesson lesson) {
        lesson.setId(id);
        return Result.success(scheduleService.updateLesson(lesson));
    }

    @DeleteMapping("/schedules/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> deleteLesson(@PathVariable Long id) {
        scheduleService.deleteLesson(id);
        return Result.success();
    }

    // ========== 教室 ==========

    @GetMapping("/classrooms")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Classroom>> listClassrooms(PageQuery query) {
        return Result.success(PageResult.of(scheduleService.pageClassrooms((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/classrooms/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Classroom> getClassroom(@PathVariable Long id) {
        return Result.success(scheduleService.getClassroomById(id));
    }

    @PostMapping("/classrooms")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Classroom> createClassroom(@Valid @RequestBody Classroom classroom) {
        return Result.success(scheduleService.createClassroom(classroom));
    }

    @PutMapping("/classrooms/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Classroom> updateClassroom(@PathVariable Long id, @RequestBody Classroom classroom) {
        classroom.setId(id);
        return Result.success(scheduleService.updateClassroom(classroom));
    }

    @DeleteMapping("/classrooms/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> deleteClassroom(@PathVariable Long id) {
        scheduleService.deleteClassroom(id);
        return Result.success();
    }

    // ========== 教室预约 ==========

    @GetMapping("/room-bookings")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<RoomBooking>> listRoomBookings(PageQuery query) {
        return Result.success(PageResult.of(scheduleService.pageRoomBookings((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping("/room-bookings")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<RoomBooking> createRoomBooking(@RequestBody RoomBooking booking) {
        booking.setApplicantId(CurrentUserHolder.get().getUserId());
        return Result.success(scheduleService.createRoomBooking(booking));
    }

    // ========== 调课申请 ==========

    @GetMapping("/schedule-adjust-requests")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<ScheduleAdjustRequest>> listAdjustRequests(PageQuery query) {
        return Result.success(PageResult.of(scheduleService.pageAdjustRequests((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping("/schedule-adjust-requests")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "TEACHER"})
    public Result<ScheduleAdjustRequest> createAdjustRequest(@RequestBody ScheduleAdjustRequest request) {
        request.setApplicantId(CurrentUserHolder.get().getUserId());
        request.setStatus(1);
        return Result.success(scheduleService.createAdjustRequest(request));
    }

    @PutMapping("/schedule-adjust-requests/{id}/audit")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleAdjustRequest> auditAdjustRequest(@PathVariable Long id, @RequestParam Integer status, @RequestParam(required = false) String remark) {
        LoginUser loginUser = CurrentUserHolder.get();
        ScheduleAdjustRequest result = scheduleService.auditAdjustRequest(id, status, loginUser.getUserId(), remark);
        // 通知申请人
        try {
            Notification n = new Notification();
            n.setUserId(result.getApplicantId());
            n.setType("SCHEDULE_CHANGE");
            n.setTitle(status == 2 ? "调课申请已通过" : "调课申请已驳回");
            n.setRelatedId(result.getId());
            notificationService.send(result.getApplicantId(), n);
        } catch (Exception ignored) {}
        return Result.success(result);
    }
}
