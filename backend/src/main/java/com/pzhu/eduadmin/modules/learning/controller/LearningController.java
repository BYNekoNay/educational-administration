package com.pzhu.eduadmin.modules.learning.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.learning.service.LearningService;
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
public class LearningController {

    private final LearningService learningService;
    private final ParentStudentMapper parentStudentMapper;

    // ---- 教务端 ----

    @GetMapping("/edu/homeworks")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Homework>> listHomeworks(PageQuery query) {
        return Result.success(PageResult.of(learningService.pageHomeworks((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping("/edu/homeworks")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "TEACHER"})
    public Result<Homework> createHomework(@RequestBody Homework homework) {
        if (homework.getTeacherId() == null) {
            homework.setTeacherId(CurrentUserHolder.get().getUserId());
        }
        return Result.success(learningService.createHomework(homework));
    }

    @GetMapping("/edu/learning-records")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<LearningRecord>> listLearningRecords(PageQuery query) {
        return Result.success(PageResult.of(learningService.pageLearningRecords((int) query.getPageNum(), (int) query.getPageSize())));
    }

    // ---- 教师端 ----

    @GetMapping("/teacher/homeworks/{lessonId}")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<Homework>> lessonHomeworks(@PathVariable Long lessonId) {
        return Result.success(learningService.getHomeworksByLessonId(lessonId));
    }

    @PostMapping({"/teacher/lessons/{lessonId}/homeworks", "/teacher/lessons/{lessonId}/homework"})
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Homework> createLessonHomework(@PathVariable Long lessonId,
                                                  @RequestBody Homework homework) {
        homework.setLessonId(lessonId);
        homework.setTeacherId(CurrentUserHolder.get().getUserId());
        return Result.success(learningService.createHomework(homework));
    }

    @GetMapping("/teacher/lessons/{lessonId}/learning-records")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<LearningRecord>> lessonLearningRecords(@PathVariable Long lessonId) {
        return Result.success(learningService.getRecordsByLessonId(lessonId));
    }

    @PostMapping("/teacher/lessons/{lessonId}/learning-records")
    @RequireRole({"TEACHER", "SUPER_ADMIN", "EDU_ADMIN"})
    public Result<List<LearningRecord>> batchCreateRecords(@PathVariable Long lessonId,
                                                            @RequestBody List<LearningRecord> records) {
        for (LearningRecord r : records) {
            r.setLessonId(lessonId);
        }
        return Result.success(learningService.batchCreateRecords(records));
    }

    // ---- 家长端 ----

    @GetMapping("/parent/learning-records")
    @Deprecated
    public Result<List<LearningRecord>> myChildRecords(@RequestParam Long lessonId,
                                                        @RequestParam(defaultValue = "0") Long studentId) {
        return childLearningRecords(lessonId, studentId);
    }

    @GetMapping("/parent/students/{studentId}/learning-records")
    public Result<List<LearningRecord>> childLearningRecords(
            @RequestParam(required = false) Long lessonId,
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
        if (lessonId != null) {
            return Result.success(learningService.getRecordsByLessonIdAndStudentId(lessonId, studentId));
        }
        return Result.success(learningService.getRecordsByStudentId(studentId));
    }

    @GetMapping("/parent/homeworks")
    @Deprecated
    public Result<List<Homework>> myChildHomeworks(@RequestParam Long lessonId) {
        return Result.success(learningService.getHomeworksByLessonId(lessonId));
    }

    @GetMapping("/parent/students/{studentId}/homeworks")
    public Result<List<Homework>> childHomeworks(@RequestParam Long lessonId,
                                                  @PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(learningService.getHomeworksByLessonId(lessonId));
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
