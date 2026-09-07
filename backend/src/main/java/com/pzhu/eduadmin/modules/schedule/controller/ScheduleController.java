package com.pzhu.eduadmin.modules.schedule.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.dto.AdjustRequestVO;
import com.pzhu.eduadmin.modules.schedule.dto.AutoScheduleRequest;
import com.pzhu.eduadmin.modules.schedule.dto.QuickAdjustRequest;
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
        ScheduleLesson lesson = scheduleService.getLessonById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        return Result.success(lesson);
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
        // M8 fix: 限制批量大小，防止 O(n²) 冲突检测耗尽资源
        if (lessons.size() > 200) {
            throw new BusinessException(400, "单次批量排课不能超过200条");
        }
        // Mass assignment protection: strip server-controlled fields
        lessons.forEach(lesson -> {
            lesson.setId(null);
            lesson.setIsDeleted(null);
            lesson.setCreateTime(null);
            lesson.setUpdateTime(null);
            // High fix: sourceLessonId 仅由调课审批流程设置，决定薪资主/代课分类，禁止客户端指定
            lesson.setSourceLessonId(null);
        });
        scheduleService.batchCreate(lessons);
        return Result.success();
    }

    @PostMapping("/schedules/auto")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<ScheduleLesson>> autoSchedule(@Valid @RequestBody AutoScheduleRequest request) {
        return Result.success(scheduleService.autoSchedule(request));
    }

    @PostMapping("/schedules")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleLesson> createLesson(@Valid @RequestBody ScheduleLesson lesson) {
        // Mass assignment protection: strip server-controlled fields
        lesson.setId(null);
        lesson.setIsDeleted(null);
        lesson.setCreateTime(null);
        lesson.setUpdateTime(null);
        // High fix: sourceLessonId 仅由调课审批流程设置，决定薪资主/代课分类，禁止客户端指定
        lesson.setSourceLessonId(null);
        // Medium fix: 与 batchCreate/autoSchedule 一致，新建课次强制为待上课（status=1），
        // 否则客户端传入 status=4 等会绕过冲突检测（仅查 status IN 1,2）导致重复排课
        lesson.setStatus(1);
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
        // Mass assignment protection: strip server-controlled fields
        lesson.setIsDeleted(null);
        lesson.setCreateTime(null);
        lesson.setUpdateTime(null);
        return Result.success(scheduleService.updateLesson(lesson));
    }

    /** P1 教务快速调课：直接更新原课次时间（免审批），成功后通知该班教师与学员家长 */
    @PostMapping("/schedules/{id}/quick-adjust")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<ScheduleLesson> quickAdjust(@PathVariable Long id,
                                              @Valid @RequestBody QuickAdjustRequest request) {
        // 快速调课只接受 目标日期/起止时间/原因，天然无 mass-assignment 面；
        // 禁止改班级/教师/教室/状态/来源课次等字段。
        return Result.success(scheduleService.quickAdjustLesson(id, request));
    }

    /** P1 拖拽确认前查询影响范围（该班教师 + 在班学员家长去重数） */
    @GetMapping("/schedules/{id}/notify-scope")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Map<String, Object>> notifyScope(@PathVariable Long id) {
        return Result.success(scheduleService.getNotifyScope(id));
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
        Classroom classroom = scheduleService.getClassroomById(id);
        if (classroom == null) {
            throw new BusinessException(404, "教室不存在");
        }
        return Result.success(classroom);
    }

    @PostMapping("/classrooms")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Classroom> createClassroom(@Valid @RequestBody Classroom classroom) {
        // Mass assignment protection: strip server-controlled fields
        classroom.setId(null);
        classroom.setIsDeleted(null);
        classroom.setCreateTime(null);
        classroom.setUpdateTime(null);
        return Result.success(scheduleService.createClassroom(classroom));
    }

    @PutMapping("/classrooms/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Classroom> updateClassroom(@PathVariable Long id, @RequestBody Classroom classroom) {
        classroom.setId(id);
        // Mass assignment protection: strip server-controlled fields
        classroom.setIsDeleted(null);
        classroom.setCreateTime(null);
        classroom.setUpdateTime(null);
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
        // Mass assignment protection: strip server-controlled fields
        booking.setId(null);
        booking.setIsDeleted(null);
        booking.setCreateTime(null);
        booking.setUpdateTime(null);
        booking.setApplicantId(CurrentUserHolder.get().getUserId());
        return Result.success(scheduleService.createRoomBooking(booking));
    }

    // ========== 调课申请 ==========

    @GetMapping("/schedule-adjust-requests")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<AdjustRequestVO>> listAdjustRequests(PageQuery query, @RequestParam(required = false) Integer status) {
        return Result.success(PageResult.of(scheduleService.pageAdjustRequests((int) query.getPageNum(), (int) query.getPageSize(), status)));
    }

    @PostMapping("/schedule-adjust-requests")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "TEACHER"})
    public Result<ScheduleAdjustRequest> createAdjustRequest(@RequestBody ScheduleAdjustRequest request) {
        // Mass assignment protection: strip server-controlled fields
        request.setId(null);
        request.setIsDeleted(null);
        request.setCreateTime(null);
        request.setUpdateTime(null);
        request.setAuditorId(null);
        request.setAuditRemark(null);
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
