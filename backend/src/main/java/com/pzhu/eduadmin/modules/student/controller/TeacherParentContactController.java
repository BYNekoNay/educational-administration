package com.pzhu.eduadmin.modules.student.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.dto.ParentContactVO;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RateLimitService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 教师单条查看家长联系方式。
 * <p><b>为什么不放在 StudentController</b>：那个类是 {@code /api/edu/students}
 * 且锁了 SUPER_ADMIN/EDU_ADMIN，教师调不到；而把这个接口的权限放宽到
 * 那个类上，会连带放开学员增删改。所以单独成类。
 *
 * <p><b>为什么不复用 verifyTeacherStudentAccess</b>：它判的是
 * "曾经在该班有过任意课次"（未过滤 status 与日期），离职教师凭历史课次
 * 就能永久查看家长电话，正好推翻"离职后锁死联系方式"。
 */
@RestController
@RequestMapping("/api/teacher/students")
@RequiredArgsConstructor
@RequireRole({"TEACHER"})
public class TeacherParentContactController {

    /** 每分钟最多查看 60 次，防止用单条接口刷全机构家长电话 */
    private static final int RATE_LIMIT_PER_MINUTE = 60;

    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final UserMapper userMapper;
    private final RateLimitService rateLimitService;
    private final OperationLogService operationLogService;

    @GetMapping("/{studentId}/parent-contact")
    public Result<List<ParentContactVO>> parentContact(@PathVariable Long studentId) {
        LoginUser me = CurrentUserHolder.get();
        Long teacherId = me.getUserId();

        if (!rateLimitService.tryAcquire("parent-contact:" + teacherId, RATE_LIMIT_PER_MINUTE)) {
            throw new BusinessException(429, "查看过于频繁，请稍后再试");
        }
        if (!canViewStudent(teacherId, studentId)) {
            throw new BusinessException(403, "无权查看该学员的联系方式");
        }

        List<ParentStudent> links = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getStudentId, studentId));
        if (links.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<Long> parentIds = links.stream()
                .map(ParentStudent::getParentUserId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        List<User> parents = userMapper.selectBatchIds(parentIds);
        List<ParentContactVO> result = new ArrayList<>();
        for (User parent : parents) {
            ParentContactVO vo = new ParentContactVO();
            vo.setParentUserId(parent.getId());
            vo.setParentName(parent.getRealName());
            vo.setPhone(parent.getPhone());
            result.add(vo);
        }

        // 审计到人 + 到对象：事后可回答"谁在什么时候看了哪个学员的家长电话"
        operationLogService.log("教师管理", "查看家长联系方式（学员=" + studentId + "）", "STUDENT", studentId);

        return Result.success(result);
    }

    /**
     * 任教关系判权，两个条件取并集：
     * <pre>
     * (A) 是该班当前任课教师（class_group.teacher_id = 我）且班级在用、学员在班
     * (B) 该班有我今天或以后的未完成课次且学员在班
     * </pre>
     * 两条都排除了历史，离职教师无法凭旧课次继续查看。
     */
    private boolean canViewStudent(Long teacherId, Long studentId) {
        Set<Long> classIds = new HashSet<>();

        // (A) 当前任教的班级
        classGroupMapper.selectList(new LambdaQueryWrapper<ClassGroup>()
                        .select(ClassGroup::getId)
                        .eq(ClassGroup::getTeacherId, teacherId)
                        .eq(ClassGroup::getStatus, 1))
                .forEach(g -> classIds.add(g.getId()));

        // (B) 今天及以后仍有未完成课次的班级
        scheduleLessonMapper.selectList(new LambdaQueryWrapper<ScheduleLesson>()
                        .select(ScheduleLesson::getClassId)
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 1)
                        .ge(ScheduleLesson::getLessonDate, LocalDate.now()))
                .forEach(l -> {
                    if (l.getClassId() != null) {
                        classIds.add(l.getClassId());
                    }
                });

        if (classIds.isEmpty()) {
            return false;
        }

        // 学员必须仍在这些班里（status=1 在班）
        Long count = classStudentMapper.selectCount(new LambdaQueryWrapper<ClassStudent>()
                .in(ClassStudent::getClassId, classIds)
                .eq(ClassStudent::getStudentId, studentId)
                .eq(ClassStudent::getStatus, 1));

        return count != null && count > 0;
    }
}
