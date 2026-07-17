package com.pzhu.eduadmin.modules.enrollment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentClassVO;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentService;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.service.FinanceService;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
@RequireRole("PARENT")
public class ParentController {

    private final CourseMapper courseMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final UserMapper userMapper;
    private final EnrollmentService enrollmentService;
    private final EnrollmentMapper enrollmentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentMapper studentMapper;
    private final NoticeMapper noticeMapper;
    private final FinanceService financeService;

    @GetMapping("/courses")
    public Result<List<Course>> listCourses() {
        return Result.success(courseMapper.selectList(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getStatus, 1)));
    }

    /**
     * 家长查看某课程下所有开班（用于在线报名选班）
     * 返回字段：班级名、教师名、起课时间、容量、当前人数、课次摘要
     */
    @GetMapping("/courses/{courseId}/classes")
    public Result<List<ParentClassVO>> listCourseClasses(@PathVariable Long courseId) {
        // 1. 取课程下所有 status=1 的开班
        List<ClassGroup> classes = courseMapper.selectClassGroupsByCourseId(courseId);
        if (classes.isEmpty()) {
            return Result.success(Collections.emptyList());
        }

        // 2. 一次性查出所有教师姓名（避免 N+1）
        Set<Long> teacherIds = classes.stream()
                .map(ClassGroup::getTeacherId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> teacherNameMap = teacherIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getRealName));

        // 3. 一次性统计每个班的当前人数
        List<Long> classIds = classes.stream().map(ClassGroup::getId).toList();
        Map<Long, Long> studentCountMap = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .in(ClassStudent::getClassId, classIds)
                        .eq(ClassStudent::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(ClassStudent::getClassId, Collectors.counting()));

        // 4. 一次性查所有班的课次，聚合出"周三/周五 19:00-20:30"摘要
        Map<Long, String> scheduleSummaryMap = summarizeSchedules(classIds);

        // 5. 组装 VO
        List<ParentClassVO> result = classes.stream().map(c -> {
            ParentClassVO vo = new ParentClassVO();
            vo.setId(c.getId());
            vo.setClassName(c.getClassName());
            vo.setTeacherId(c.getTeacherId());
            vo.setTeacherName(teacherNameMap.getOrDefault(c.getTeacherId(), "未指定"));
            vo.setStartDate(c.getStartDate());
            vo.setMaxStudentCount(c.getMaxStudentCount() == null ? 0 : c.getMaxStudentCount());
            vo.setCurrentStudentCount(studentCountMap.getOrDefault(c.getId(), 0L).intValue());
            vo.setStatus(c.getStatus());
            vo.setScheduleSummary(scheduleSummaryMap.getOrDefault(c.getId(), "课次待定"));
            return vo;
        }).toList();

        return Result.success(result);
    }

    /**
     * 聚合班的课次摘要：取未来 8 周内最早的若干课次，归纳成"周X HH:MM-HH:MM"格式
     */
    private Map<Long, String> summarizeSchedules(List<Long> classIds) {
        if (classIds.isEmpty()) return Collections.emptyMap();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusWeeks(8);

        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, classIds)
                        .eq(ScheduleLesson::getStatus, 1)
                        .between(ScheduleLesson::getLessonDate, today, horizon)
                        .orderByAsc(ScheduleLesson::getClassId, ScheduleLesson::getLessonDate,
                                ScheduleLesson::getStartTime));

        Map<Long, List<ScheduleLesson>> grouped = lessons.stream()
                .collect(Collectors.groupingBy(ScheduleLesson::getClassId));

        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");
        Map<Long, String> result = new HashMap<>();
        for (Map.Entry<Long, List<ScheduleLesson>> e : grouped.entrySet()) {
            List<ScheduleLesson> list = e.getValue();
            // 取前 3 条（代表典型的周次）
            Set<DayOfWeek> days = list.stream().limit(3)
                    .map(l -> l.getLessonDate().getDayOfWeek())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            String dayDesc = days.stream()
                    .map(d -> dayOfWeekLabel(d))
                    .collect(Collectors.joining("/"));
            String timeDesc = list.get(0).getStartTime().format(timeFmt) + "-"
                    + list.get(0).getEndTime().format(timeFmt);
            result.put(e.getKey(), dayDesc + " " + timeDesc);
        }
        return result;
    }

    private static String dayOfWeekLabel(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }

    @PostMapping("/enrollments")
    public Result<Enrollment> createEnrollment(@RequestBody Enrollment enrollment) {
        if (enrollment.getStudentId() == null) {
            throw new BusinessException(400, "学员ID不能为空");
        }
        if (enrollment.getCourseId() == null) {
            throw new BusinessException(400, "课程ID不能为空");
        }
        Course course = courseMapper.selectById(enrollment.getCourseId());
        if (course == null || course.getStatus() != 1) {
            throw new BusinessException(404, "课程不存在或已下架");
        }
        LoginUser loginUser = CurrentUserHolder.get();
        // Check parent-student binding
        Long bindingCount = parentStudentMapper.selectCount(new LambdaQueryWrapper<ParentStudent>()
                .eq(ParentStudent::getParentUserId, loginUser.getUserId())
                .eq(ParentStudent::getStudentId, enrollment.getStudentId()));
        if (bindingCount == 0) {
            throw new BusinessException(403, "无权为该学员报名");
        }
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

    /**
     * 查询指定学员在当前课程下是否有活跃报名记录（排除终态）
     * 前端用于"已报名"标签显示
     */
    @GetMapping("/enrollments/check/{studentId}/{courseId}")
    public Result<Boolean> checkEnrollmentExists(
            @PathVariable Long studentId,
            @PathVariable Long courseId) {
        LoginUser loginUser = CurrentUserHolder.get();
        // 验证家长与学员的绑定关系
        Long bindingCount = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, loginUser.getUserId())
                        .eq(ParentStudent::getStudentId, studentId));
        if (bindingCount == 0) {
            throw new BusinessException(403, "无权查看该学员信息");
        }
        Long count = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .eq(Enrollment::getCourseId, courseId)
                        .notIn(Enrollment::getStatus, List.of(4, 5)));
        return Result.success(count > 0);
    }

    /**
     * 家长自助模拟缴费（仅支持 status=2 待缴费状态的报名）
     * 后端按 enrollment → student → course 拿到价格，构造 PaymentRecord 后走 FinanceService.createPayment
     */
    @PostMapping("/payments")
    public Result<PaymentRecord> mockPayment(@RequestBody Map<String, Long> body) {
        Long enrollmentId = body.get("enrollmentId");
        if (enrollmentId == null) {
            throw new BusinessException(400, "报名ID不能为空");
        }
        Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        LoginUser loginUser = CurrentUserHolder.get();
        // 验证家长与学员的绑定关系
        Long bindingCount = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, loginUser.getUserId())
                        .eq(ParentStudent::getStudentId, enrollment.getStudentId()));
        if (bindingCount == 0) {
            throw new BusinessException(403, "无权为该学员缴费");
        }
        if (!Integer.valueOf(2).equals(enrollment.getStatus())) {
            throw new BusinessException(409, "仅待缴费状态的报名可支付");
        }
        // 从课程取课时数与价格（绕过 @TableLogic 防止已软删课程拿不到）
        Course course = courseMapper.selectByIdIncludeDeleted(enrollment.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "关联课程不存在");
        }
        // 构造 PaymentRecord
        PaymentRecord record = new PaymentRecord();
        record.setEnrollmentId(enrollmentId);
        record.setStudentId(enrollment.getStudentId());
        record.setCourseId(enrollment.getCourseId());
        record.setLessonCount(new java.math.BigDecimal(course.getTotalLessons()));
        record.setAmount(course.getPrice());
        record.setPayType(2);  // 2-模拟支付
        record.setPayTime(LocalDateTime.now());
        record.setOperatorId(loginUser.getUserId());
        record.setOperatorRole("PARENT");
        record.setRemark("家长自助模拟缴费");
        return Result.success(financeService.createPayment(record));
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
                        .in(Notice::getReceiverType, "ALL", "PARENT")
                        .orderByDesc(Notice::getCreateTime)
                        .last("LIMIT 20")));
    }

    @GetMapping("/payments")
    public Result<List<PaymentRecord>> listPayments(@RequestParam(required = false) Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        List<Long> studentIds;
        if (studentId != null) {
            // verify this student belongs to the parent
            long count = parentStudentMapper.selectCount(
                    new LambdaQueryWrapper<ParentStudent>()
                            .eq(ParentStudent::getParentUserId, parentUserId)
                            .eq(ParentStudent::getStudentId, studentId));
            if (count == 0) return Result.success(Collections.emptyList());
            studentIds = List.of(studentId);
        } else {
            studentIds = parentStudentMapper.selectList(
                    new LambdaQueryWrapper<ParentStudent>().eq(ParentStudent::getParentUserId, parentUserId))
                    .stream().map(ParentStudent::getStudentId).toList();
        }
        if (studentIds.isEmpty()) return Result.success(Collections.emptyList());
        List<PaymentRecord> payments = financeService.getPaymentsByStudentIds(studentIds);
        // 分页保护：最多返回 200 条
        if (payments.size() > 200) {
            payments = payments.subList(0, 200);
        }
        return Result.success(payments);
    }
}
