package com.pzhu.eduadmin.modules.exam.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.service.ExamService;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.security.RequireRole;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/edu/exams")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class ExamController {

    private final ExamService examService;
    private final NotificationService notificationService;
    private final ParentStudentMapper parentStudentMapper;

    @GetMapping("/levels")
    public Result<PageResult<ExamLevel>> listExamLevels(PageQuery query) {
        return Result.success(PageResult.of(examService.pageExamLevels((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/levels/{id}")
    public Result<ExamLevel> getExamLevel(@PathVariable Long id) {
        return Result.success(examService.getExamLevelById(id));
    }

    @PostMapping("/levels")
    public Result<ExamLevel> createExamLevel(@Valid @RequestBody ExamLevel examLevel) {
        return Result.success(examService.createExamLevel(examLevel));
    }

    @PutMapping("/levels/{id}")
    public Result<ExamLevel> updateExamLevel(@PathVariable Long id, @RequestBody ExamLevel examLevel) {
        examLevel.setId(id);
        return Result.success(examService.updateExamLevel(examLevel));
    }

    @DeleteMapping("/levels/{id}")
    public Result<Void> deleteExamLevel(@PathVariable Long id) {
        examService.deleteExamLevel(id);
        return Result.success();
    }

    @GetMapping("/signups")
    public Result<PageResult<ExamSignup>> listExamSignups(PageQuery query) {
        return Result.success(PageResult.of(examService.pageExamSignups((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @PostMapping("/signups")
    public Result<ExamSignup> createExamSignup(@Valid @RequestBody ExamSignup signup) {
        ExamSignup result = examService.createExamSignup(signup);
        notifyParents(result);
        return Result.success(result);
    }

    private void notifyParents(ExamSignup signup) {
        try {
            ExamLevel level = examService.getExamLevelById(signup.getExamId());
            String examName = level != null ? level.getName() : "考级";
            // 查学生的所有家长
            java.util.List<Long> parentUserIds = parentStudentMapper.selectList(
                    new LambdaQueryWrapper<ParentStudent>()
                            .eq(ParentStudent::getStudentId, signup.getStudentId()))
                    .stream().map(ParentStudent::getParentUserId).collect(Collectors.toList());
            if (!parentUserIds.isEmpty()) {
                Notification n = new Notification();
                n.setType("EXAM_NOTICE");
                n.setTitle("考级报名通知");
                n.setContent("学员已报名" + examName + "考级");
                n.setRelatedId(signup.getId());
                notificationService.sendToUsers(parentUserIds, n);
            }
        } catch (Exception ignored) {}
    }

    @PutMapping("/signups/{id}")
    public Result<ExamSignup> updateExamSignup(@PathVariable Long id, @RequestBody ExamSignup signup) {
        signup.setId(id);
        return Result.success(examService.updateExamSignup(signup));
    }

    @GetMapping("/archives")
    public Result<PageResult<ExamSignup>> listArchives(PageQuery query) {
        return Result.success(PageResult.of(examService.pageArchives(
                (int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword())));
    }
}
