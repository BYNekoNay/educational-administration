package com.pzhu.eduadmin.modules.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.finance.service.FinanceService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/parent")
@RequiredArgsConstructor
@RequireRole("PARENT")
public class ParentRefundController {

    private final FinanceService financeService;
    private final EnrollmentMapper enrollmentMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final CourseMapper courseMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentMapper studentMapper;

    /**
     * 可退费报名列表：status=3(已缴费)且无待审核退费的报名
     */
    @GetMapping("/refunds/available")
    public Result<List<Map<String, Object>>> available(@RequestParam(required = false) Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        List<Long> studentIds = resolveStudentIds(parentUserId, studentId);
        if (studentIds.isEmpty()) return Result.success(Collections.emptyList());

        // 已缴费的报名
        List<Enrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .in(Enrollment::getStudentId, studentIds)
                        .eq(Enrollment::getStatus, 3));
        if (enrollments.isEmpty()) return Result.success(Collections.emptyList());

        // 过滤已存在待审核退费的报名
        Set<Long> enrollmentIds = enrollments.stream().map(Enrollment::getId).collect(Collectors.toSet());
        Set<Long> pendingRefundIds = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .in(RefundRecord::getEnrollmentId, enrollmentIds)
                        .eq(RefundRecord::getStatus, 1))
                .stream().map(RefundRecord::getEnrollmentId).collect(Collectors.toSet());

        List<Enrollment> available = enrollments.stream()
                .filter(e -> !pendingRefundIds.contains(e.getId()))
                .toList();
        if (available.isEmpty()) return Result.success(Collections.emptyList());

        // 批量取 student / course name
        Set<Long> sIds = available.stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
        Set<Long> cIds = available.stream().map(Enrollment::getCourseId).collect(Collectors.toSet());
        Map<Long, String> studentNameMap = studentMapper.selectBatchIds(sIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        Map<Long, String> courseNameMap = new HashMap<>();
        for (Long cid : cIds) {
            Course c = courseMapper.selectById(cid);
            if (c != null) courseNameMap.put(cid, c.getName());
        }

        // 取缴费金额和剩余课时
        List<PaymentRecord> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().in(PaymentRecord::getEnrollmentId, enrollmentIds));
        Map<Long, BigDecimal> paidMap = new HashMap<>();
        for (PaymentRecord p : payments) {
            paidMap.merge(p.getEnrollmentId(), p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
        }

        List<LessonAccount> accounts = lessonAccountMapper.selectList(
                new LambdaQueryWrapper<LessonAccount>()
                        .in(LessonAccount::getStudentId, sIds)
                        .in(LessonAccount::getCourseId, cIds));
        // key = studentId_courseId
        Map<String, BigDecimal> remainMap = new HashMap<>();
        for (LessonAccount a : accounts) {
            String key = a.getStudentId() + "_" + a.getCourseId();
            remainMap.put(key, a.getRemainingLessons() != null ? a.getRemainingLessons() : BigDecimal.ZERO);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Enrollment e : available) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("enrollmentId", e.getId());
            m.put("studentId", e.getStudentId());
            m.put("studentName", studentNameMap.getOrDefault(e.getStudentId(), ""));
            m.put("courseId", e.getCourseId());
            m.put("courseName", courseNameMap.getOrDefault(e.getCourseId(), ""));
            m.put("paidAmount", paidMap.getOrDefault(e.getId(), BigDecimal.ZERO));
            m.put("remainingLessons", remainMap.getOrDefault(e.getStudentId() + "_" + e.getCourseId(), BigDecimal.ZERO));
            result.add(m);
        }
        return Result.success(result);
    }

    /**
     * 家长提交退费申请
     */
    @PostMapping("/refunds")
    public Result<RefundRecord> create(@RequestBody Map<String, Object> body) {
        Long enrollmentId = toLong(body.get("enrollmentId"));
        if (enrollmentId == null) throw new BusinessException(400, "报名ID不能为空");

        Long parentUserId = CurrentUserHolder.get().getUserId();
        Enrollment enrollment = enrollmentMapper.selectById(enrollmentId);
        if (enrollment == null) throw new BusinessException(404, "报名记录不存在");

        // 验证家长-学员绑定
        Long bindingCount = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId)
                        .eq(ParentStudent::getStudentId, enrollment.getStudentId()));
        if (bindingCount == 0) throw new BusinessException(403, "无权为该学员申请退费");

        // Bug #12 fix: 使用累计缴费总额（含续费），而非仅最近一笔缴费金额
        BigDecimal totalPaid = paymentRecordMapper.sumByEnrollmentId(enrollmentId);
        if (totalPaid.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(404, "未找到关联缴费记录");
        }
        // 取最近一笔缴费记录用于关联引用
        PaymentRecord latestPayment = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getEnrollmentId, enrollmentId)
                        .orderByDesc(PaymentRecord::getCreateTime)
                        .last("LIMIT 1"));

        // 取剩余课时
        LessonAccount account = lessonAccountMapper.selectOne(
                new LambdaQueryWrapper<LessonAccount>()
                        .eq(LessonAccount::getStudentId, enrollment.getStudentId())
                        .eq(LessonAccount::getCourseId, enrollment.getCourseId()));

        RefundRecord record = new RefundRecord();
        record.setStudentId(enrollment.getStudentId());
        record.setEnrollmentId(enrollmentId);
        record.setPaymentRecordId(latestPayment != null ? latestPayment.getId() : null);
        record.setApplicantId(parentUserId);
        record.setApplicantRole("PARENT");

        // 退费金额 = 累计已缴金额 × (剩余课时 / 原始总课时)，按比例扣除已上课时费用
        // M1 fix: 使用稳定的原始总课时（不随退费缩小）作为分母，与审核端计算一致
        if (account != null) {
            record.setLessonCount(account.getRemainingLessons());
            BigDecimal remain = account.getRemainingLessons() != null ? account.getRemainingLessons() : BigDecimal.ZERO;
            BigDecimal originalTotal = paymentRecordMapper.sumLessonCountByEnrollmentId(enrollmentId);
            if (originalTotal != null && originalTotal.compareTo(BigDecimal.ZERO) > 0) {
                record.setAmount(totalPaid.multiply(remain).divide(originalTotal, 2, java.math.RoundingMode.HALF_UP));
            } else {
                record.setAmount(totalPaid);
            }
        } else {
            record.setAmount(totalPaid);
        }

        return Result.success(financeService.createRefund(record));
    }

    /**
     * 家长查看自己的退费记录
     */
    @GetMapping("/refunds")
    public Result<PageResult<Map<String, Object>>> list(PageQuery query, @RequestParam(required = false) Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        List<Long> studentIds = resolveStudentIds(parentUserId, studentId);
        if (studentIds.isEmpty()) return Result.success(PageResult.empty());

        // 查家长的退费申请
        List<RefundRecord> records = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .in(RefundRecord::getStudentId, studentIds)
                        .eq(RefundRecord::getApplicantId, parentUserId)
                        .eq(RefundRecord::getApplicantRole, "PARENT")
                        .orderByDesc(RefundRecord::getCreateTime));

        // 手动分页
        int pageNum = (int) Math.max(1, query.getPageNum());
        int pageSize = (int) Math.min(100, Math.max(1, query.getPageSize()));
        int total = records.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<RefundRecord> page = from < total ? records.subList(from, to) : Collections.emptyList();

        // 无记录时直接返回空分页，避免 selectBatchIds(emptySet) 报 `IN ( )` 语法错
        if (page.isEmpty()) {
            return Result.success(PageResult.empty());
        }

        // 填充名称
        Set<Long> sIds = page.stream().map(RefundRecord::getStudentId).collect(Collectors.toSet());
        Set<Long> eIds = page.stream().map(RefundRecord::getEnrollmentId).collect(Collectors.toSet());
        Map<Long, String> studentNameMap = studentMapper.selectBatchIds(sIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName, (a, b) -> a));

        List<Enrollment> enrollments = enrollmentMapper.selectBatchIds(eIds);
        Map<Long, Enrollment> enrollmentMap = enrollments.stream()
                .collect(Collectors.toMap(Enrollment::getId, e -> e));

        Set<Long> courseIds = enrollments.stream().map(Enrollment::getCourseId).collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        for (Long cid : courseIds) {
            Course c = courseMapper.selectById(cid);
            if (c != null) courseNameMap.put(cid, c.getName());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (RefundRecord r : page) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", r.getId());
            m.put("studentId", r.getStudentId());
            m.put("studentName", studentNameMap.getOrDefault(r.getStudentId(), ""));
            m.put("enrollmentId", r.getEnrollmentId());
            m.put("amount", r.getAmount());
            m.put("lessonCount", r.getLessonCount());
            m.put("status", r.getStatus());
            m.put("createTime", r.getCreateTime());
            Enrollment enr = enrollmentMap.get(r.getEnrollmentId());
            if (enr != null) {
                m.put("courseName", courseNameMap.getOrDefault(enr.getCourseId(), ""));
            }
            rows.add(m);
        }
        return Result.success(PageResult.of(total, rows));
    }

    // ====== helpers ======

    private List<Long> resolveStudentIds(Long parentUserId, Long studentId) {
        if (studentId != null) {
            Long bindingCount = parentStudentMapper.selectCount(
                    new LambdaQueryWrapper<ParentStudent>()
                            .eq(ParentStudent::getParentUserId, parentUserId)
                            .eq(ParentStudent::getStudentId, studentId));
            return bindingCount > 0 ? List.of(studentId) : Collections.emptyList();
        }
        return parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>().eq(ParentStudent::getParentUserId, parentUserId))
                .stream().map(ParentStudent::getStudentId).toList();
    }

    private Long toLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try { return Long.valueOf(v.toString()); } catch (NumberFormatException e) { return null; }
    }
}
