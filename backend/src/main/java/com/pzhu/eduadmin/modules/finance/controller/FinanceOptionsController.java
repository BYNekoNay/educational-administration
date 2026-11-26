package com.pzhu.eduadmin.modules.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 财务域只读下拉选项。
 *
 * <p>财务角色对 /api/edu/* 一律 403（StudentController / EnrollmentController /
 * CourseController / ScheduleController 都只放行 SUPER_ADMIN/EDU_ADMIN），
 * 但收费、退费、薪资三个页面都需要学员/课程/报名/教师下拉。原实现让前端直接调
 * 教务接口，结果是每个页面首屏弹 3~4 次「无权访问该接口」、且 Promise.all 一拒全丢
 * 导致下拉全空。
 *
 * <p>这里只投影 id 与名称（课程额外给单价与总课时，收费页要算金额），
 * 不暴露手机号等敏感字段。
 */
@RestController
@RequestMapping("/api/finance/options")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN", "FINANCE"})
public class FinanceOptionsController {

    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final EnrollmentMapper enrollmentMapper;

    @GetMapping("/teachers")
    public Result<List<Map<String, Object>>> teachers() {
        List<User> list = userMapper.selectList(new LambdaQueryWrapper<User>()
                .eq(User::getRoleCode, "TEACHER")
                .eq(User::getStatus, 1)
                .select(User::getId, User::getRealName, User::getUsername)
                .orderByAsc(User::getId));
        return Result.success(list.stream().map(u -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", u.getId());
            m.put("name", u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/courses")
    public Result<List<Map<String, Object>>> courses() {
        List<Course> list = courseMapper.selectList(new LambdaQueryWrapper<Course>()
                .select(Course::getId, Course::getName, Course::getPrice, Course::getTotalLessons)
                .orderByAsc(Course::getId));
        return Result.success(list.stream().map(c -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", c.getId());
            m.put("name", c.getName());
            m.put("price", c.getPrice());
            m.put("totalLessons", c.getTotalLessons());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/students")
    public Result<List<Map<String, Object>>> students() {
        List<Student> list = studentMapper.selectList(new LambdaQueryWrapper<Student>()
                .select(Student::getId, Student::getName)
                .orderByDesc(Student::getId));
        return Result.success(list.stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("name", s.getName());
            return m;
        }).collect(Collectors.toList()));
    }

    @GetMapping("/enrollments")
    public Result<List<Map<String, Object>>> enrollments() {
        List<Enrollment> list = enrollmentMapper.selectList(new LambdaQueryWrapper<Enrollment>()
                .select(Enrollment::getId, Enrollment::getStudentId, Enrollment::getCourseId, Enrollment::getStatus)
                .in(Enrollment::getStatus, 2, 3)
                .orderByDesc(Enrollment::getId));
        // 批量补名称，避免 N+1
        Set<Long> sids = list.stream().map(Enrollment::getStudentId).filter(Objects::nonNull).collect(Collectors.toSet());
        Set<Long> cids = list.stream().map(Enrollment::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> sNames = sids.isEmpty() ? Map.of()
                : studentMapper.selectBatchIds(sids).stream()
                    .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));
        Map<Long, String> cNames = cids.isEmpty() ? Map.of()
                : courseMapper.selectBatchIds(cids).stream()
                    .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
        return Result.success(list.stream().map(e -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("courseId", e.getCourseId());
            m.put("status", e.getStatus());
            m.put("studentName", sNames.getOrDefault(e.getStudentId(), ""));
            m.put("courseName", cNames.getOrDefault(e.getCourseId(), ""));
            return m;
        }).collect(Collectors.toList()));
    }
}
