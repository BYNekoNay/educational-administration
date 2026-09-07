package com.pzhu.eduadmin.modules.enrollment.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.dto.ClassConflictVO;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentClassVO;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentEnrollmentSnapshotVO;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentService;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.service.FinanceService;
import com.pzhu.eduadmin.modules.notice.entity.Notice;
import com.pzhu.eduadmin.modules.notice.mapper.NoticeMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
@RequireRole("PARENT")
public class ParentController {

    private final CourseMapper courseMapper;
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
        return Result.success(enrollmentService.listParentCourseClasses(courseId));
    }

    @PostMapping("/enrollments")
    public Result<Enrollment> createEnrollment(
            @RequestBody Enrollment enrollment,
            @RequestHeader(value = "If-Match", required = false) String snapshotVersion) {
        // H4 fix: 清除客户端不应设置的服务端控制字段
        // 注意：classId 是家长在报名页明确选择的班级，不能清空——
        // EnrollmentServiceImpl.create 依赖 classId 做班级归属/开放/时间冲突校验，
        // FinanceServiceImpl.createPayment 依赖 classId 在缴费后写 ClassStudent 让学员入班。
        // 清空会导致服务端冲突校验被绕过、缴费后学员永不入班（管理端 create 不清 classId，此处应保持一致）。
        enrollment.setId(null);
        enrollment.setAuditorId(null);
        enrollment.setAuditRemark(null);
        enrollment.setHoldExpireTime(null);
        enrollment.setIsDeleted(null);
        enrollment.setCreateTime(null);
        enrollment.setUpdateTime(null);
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
        // 新版 H5 提交它实际展示的快照版本；旧客户端未传版本时，服务端即时创建
        // 一份快照再走同一校验链，保持请求兼容且不允许绕过容量/冲突复核。
        if (snapshotVersion == null || snapshotVersion.isBlank()) {
            snapshotVersion = enrollmentService.getParentEnrollmentSnapshot(
                    loginUser.getUserId(), enrollment.getStudentId()).getVersionToken();
        }
        return Result.success(enrollmentService.createParentEnrollmentFromSnapshot(
                loginUser.getUserId(), enrollment, snapshotVersion));
    }

    @GetMapping("/enrollments")
    public Result<PageResult<Enrollment>> listMyEnrollments(PageQuery query,
            @RequestParam(required = false) Long studentId) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(PageResult.of(
                enrollmentService.pageByParentUserId(loginUser.getUserId(), studentId,
                        (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/enrollments/snapshot")
    public Result<ParentEnrollmentSnapshotVO> getEnrollmentSnapshot(@RequestParam Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        return Result.success(enrollmentService.getParentEnrollmentSnapshot(parentUserId, studentId));
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
                        .notIn(Enrollment::getStatus, List.of(4, 5, 6)));  // M2 fix: 已退费(6)也属终态
        return Result.success(count > 0);
    }

    /**
     * 检测学员在当前课程的各开班中是否存在时间冲突。
     * 前端用于班级列表灰显 + 冲突标记。
     * 返回：冲突班级的 classId → ClassConflictVO 列表
     */
    @GetMapping("/enrollments/conflicts/{studentId}")
    public Result<List<ClassConflictVO>> checkConflicts(
            @PathVariable Long studentId,
            @RequestParam Long courseId) {
        LoginUser loginUser = CurrentUserHolder.get();
        // 验证家长与学员的绑定关系
        Long bindingCount = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, loginUser.getUserId())
                        .eq(ParentStudent::getStudentId, studentId));
        if (bindingCount == 0) {
            throw new BusinessException(403, "无权查看该学员信息");
        }

        List<ClassGroup> classes = courseMapper.selectClassGroupsByCourseId(courseId);
        if (classes.isEmpty()) return Result.success(Collections.emptyList());

        List<ClassConflictVO> conflicts = new ArrayList<>();
        for (ClassGroup cg : classes) {
            Map<String, Object> conflictInfo = enrollmentService.detectTimeConflict(studentId, cg.getId());
            if (conflictInfo != null) {
                conflicts.add(new ClassConflictVO(
                        cg.getId(),
                        cg.getClassName(),
                        (String) conflictInfo.get("conflictClassName"),
                        (String) conflictInfo.get("conflictDetail")));
            }
        }
        return Result.success(conflicts);
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
        // A3#7 fix: 名额保留已过期则禁止支付，避免占用已释放的名额
        if (enrollment.getHoldExpireTime() != null
                && enrollment.getHoldExpireTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(409, "名额保留已过期，无法继续支付");
        }
        // 从课程取课时数与价格（绕过 @TableLogic 防止已软删课程拿不到）
        Course course = courseMapper.selectByIdIncludeDeleted(enrollment.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "关联课程不存在");
        }
        // Bug #25 fix: 课程信息不完整时无法构造支付记录，防止 NPE
        if (course.getTotalLessons() == null || course.getPrice() == null) {
            throw new BusinessException(400, "课程信息不完整，无法支付");
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
        Long parentUserId = CurrentUserHolder.get().getUserId();
        return Result.success(noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>()
                        .in(Notice::getReceiverType, "ALL", "PARENT")
                        .and(w -> w.isNull(Notice::getReceiverId).or().eq(Notice::getReceiverId, parentUserId))
                        .and(w -> w.isNull(Notice::getPublishTime).or().le(Notice::getPublishTime, LocalDateTime.now()))
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
