package com.pzhu.eduadmin.modules.enrollment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentService;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
public class ParentController {

    private final CourseMapper courseMapper;
    private final EnrollmentService enrollmentService;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentMapper studentMapper;
    private final NoticeMapper noticeMapper;
    private final PaymentRecordMapper paymentRecordMapper;

    @GetMapping("/courses")
    public Result<List<Course>> listCourses() {
        return Result.success(courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)));
    }

    @PostMapping("/enrollments")
    public Result<Enrollment> createEnrollment(@RequestBody Enrollment enrollment) {
        LoginUser loginUser = CurrentUserHolder.get();
        enrollment.setParentUserId(loginUser.getUserId());
        enrollment.setStatus(1);
        return Result.success(enrollmentService.create(enrollment));
    }

    @GetMapping("/enrollments")
    public Result<PageResult<Enrollment>> listMyEnrollments(PageQuery query) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(PageResult.of(
                enrollmentService.pageByParentUserId(loginUser.getUserId(),
                        (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/students")
    public Result<List<Student>> listMyStudents() {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        List<ParentStudent> bindings = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId));
        if (bindings.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        Set<Long> studentIds = bindings.stream()
                .map(ParentStudent::getStudentId).collect(Collectors.toSet());
        return Result.success(studentMapper.selectBatchIds(studentIds));
    }

    @GetMapping("/notices")
    public Result<List<Notice>> listNotices() {
        return Result.success(noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>()
                        .orderByDesc(com.pzhu.eduadmin.modules.notice.entity.Notice::getCreateTime)
                        .last("LIMIT 20")));
    }

    @GetMapping("/payments")
    public Result<List<PaymentRecord>> listPayments() {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        List<Long> studentIds = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>().eq(ParentStudent::getParentUserId, parentUserId))
                .stream().map(ParentStudent::getStudentId).toList();
        if (studentIds.isEmpty()) return Result.success(Collections.emptyList());
        return Result.success(paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .in(PaymentRecord::getStudentId, studentIds)
                        .orderByDesc(PaymentRecord::getPayTime)));
    }
}
