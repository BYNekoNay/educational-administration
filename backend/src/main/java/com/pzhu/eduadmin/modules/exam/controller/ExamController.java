package com.pzhu.eduadmin.modules.exam.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.service.ExamService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu/exams")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
public class ExamController {

    private final ExamService examService;

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
        return Result.success(examService.createExamSignup(signup));
    }

    @PutMapping("/signups/{id}")
    public Result<ExamSignup> updateExamSignup(@PathVariable Long id, @RequestBody ExamSignup signup) {
        signup.setId(id);
        return Result.success(examService.updateExamSignup(signup));
    }
}
