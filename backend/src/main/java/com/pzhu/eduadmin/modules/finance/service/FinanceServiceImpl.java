package com.pzhu.eduadmin.modules.finance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.*;
import com.pzhu.eduadmin.modules.finance.mapper.*;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinanceServiceImpl implements FinanceService {

    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final LessonFlowMapper lessonFlowMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final CourseMapper courseMapper;
    private final OperationLogMapper operationLogMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;

    private static final Map<String, SFunction<PaymentRecord, ?>> PAYMENT_SORT_MAP = Map.of(
            "id", PaymentRecord::getId, "payTime", PaymentRecord::getPayTime, "amount", PaymentRecord::getAmount, "lessonCount", PaymentRecord::getLessonCount
    );
    private static final Map<String, SFunction<RefundRecord, ?>> REFUND_SORT_MAP = Map.of(
            "id", RefundRecord::getId, "createTime", RefundRecord::getCreateTime, "amount", RefundRecord::getAmount, "status", RefundRecord::getStatus
    );
    private static final Map<String, SFunction<LessonAccount, ?>> ACCOUNT_SORT_MAP = Map.of(
            "id", LessonAccount::getId, "remainingLessons", LessonAccount::getRemainingLessons, "totalLessons", LessonAccount::getTotalLessons, "expireDate", LessonAccount::getExpireDate
    );
    private static final Map<String, SFunction<LessonFlow, ?>> FLOW_SORT_MAP = Map.of(
            "id", LessonFlow::getId, "createTime", LessonFlow::getCreateTime, "changeAmount", LessonFlow::getChangeAmount
    );

    @Override
    public Page<PaymentRecord> pagePaymentRecords(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<PaymentRecord> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, PAYMENT_SORT_MAP, () -> wrapper.orderByDesc(PaymentRecord::getPayTime));
        Page<PaymentRecord> page = paymentRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populatePaymentNames(page.getRecords());
        return page;
    }

    private void populatePaymentNames(List<PaymentRecord> list) {
        if (list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(PaymentRecord::getStudentId).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(PaymentRecord::getCourseId).collect(Collectors.toSet());
        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
        for (PaymentRecord p : list) {
            p.setStudentName(studentNames.getOrDefault(p.getStudentId(), ""));
            p.setCourseName(courseNames.getOrDefault(p.getCourseId(), ""));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord createPayment(PaymentRecord record) {
        paymentRecordMapper.insert(record);

        Enrollment enrollment = enrollmentMapper.selectById(record.getEnrollmentId());
        if (enrollment == null) throw new BusinessException(404, "报名记录不存在");
        if (enrollment.getStatus() != 2 && enrollment.getStatus() != 1) {
            throw new BusinessException(409, "当前报名状态不可缴费");
        }

        LessonAccount account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                .eq(LessonAccount::getStudentId, record.getStudentId())
                .eq(LessonAccount::getCourseId, record.getCourseId()));
        BigDecimal beforeBalance = BigDecimal.ZERO;
        if (account == null) {
            account = new LessonAccount();
            account.setStudentId(record.getStudentId());
            account.setCourseId(record.getCourseId());
            account.setTotalLessons(record.getLessonCount());
            account.setRemainingLessons(record.getLessonCount());
            account.setExpireDate(LocalDate.now().plusYears(1));
            account.setVersion(0);
            lessonAccountMapper.insert(account);
        } else {
            beforeBalance = account.getRemainingLessons();
            account.setTotalLessons(account.getTotalLessons().add(record.getLessonCount()));
            account.setRemainingLessons(account.getRemainingLessons().add(record.getLessonCount()));
            int rows = lessonAccountMapper.updateById(account);
            if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");
        }

        LessonFlow flow = new LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(record.getStudentId());
        flow.setSourceType(1);
        flow.setSourceId(record.getId());
        flow.setChangeAmount(record.getLessonCount());
        flow.setChangeType(1);
        flow.setBeforeBalance(beforeBalance);
        flow.setAfterBalance(account.getRemainingLessons());
        flow.setRemark(record.getRemark());
        lessonFlowMapper.insert(flow);

        enrollment.setStatus(3);
        enrollmentMapper.updateById(enrollment);

        if (enrollment.getClassId() != null) {
            ClassGroup classGroup = classGroupMapper.selectById(enrollment.getClassId());
            long currentCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, enrollment.getClassId()));
            if (currentCount >= classGroup.getMaxStudentCount()) {
                throw new BusinessException(409, "班级已满，无法入班");
            }
            ClassStudent cs = new ClassStudent();
            cs.setClassId(enrollment.getClassId());
            cs.setStudentId(record.getStudentId());
            cs.setStatus(1);
            classStudentMapper.insert(cs);
        }

        // 操作日志
        logOperation("财务管理", "登记收费(studentId=" + record.getStudentId() + ",金额=" + record.getAmount() + ",id=" + record.getId() + ")");

        return record;
    }

    @Override
    public Page<RefundRecord> pageRefundRecords(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<RefundRecord> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, REFUND_SORT_MAP, () -> wrapper.orderByDesc(RefundRecord::getCreateTime));
        Page<RefundRecord> page = refundRecordMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateRefundNames(page.getRecords());
        return page;
    }

    private void populateRefundNames(List<RefundRecord> list) {
        if (list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(RefundRecord::getStudentId).collect(Collectors.toSet());
        Set<Long> applicantIds = list.stream().map(RefundRecord::getApplicantId).collect(Collectors.toSet());
        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        Map<Long, String> userNames = userMapper.selectBatchIds(applicantIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        for (RefundRecord r : list) {
            r.setStudentName(studentNames.getOrDefault(r.getStudentId(), ""));
            r.setApplicantName(userNames.getOrDefault(r.getApplicantId(), ""));
        }
    }

    @Override
    public RefundRecord createRefund(RefundRecord record) {
        record.setStatus(1);
        refundRecordMapper.insert(record);
        return record;
    }

    // ==================== 退费审核 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundRecord auditRefund(Long id, Integer status, Long auditorId, BigDecimal refundAmount) {
        RefundRecord record = refundRecordMapper.selectById(id);
        if (record == null) throw new BusinessException(404, "退费记录不存在");
        if (record.getStatus() != 1) {
            throw new BusinessException(409, "仅可审核待审核状态的退费申请");
        }

        // 同人隔离检测
        if (record.getApplicantId().equals(auditorId)) {
            throw new BusinessException(409, "审核人与申请人不可为同一人，请转交其他财务人员复核");
        }

        if (status == 2) {
            // 审核通过 — 完整事务链路
            processRefundApproval(record, refundAmount);
        }

        record.setStatus(status);
        record.setAuditorId(auditorId);
        if (refundAmount != null) record.setAmount(refundAmount);
        refundRecordMapper.updateById(record);

        // 操作日志
        logOperation("财务管理", status == 2 ? "审核通过退费(id=" + id + ")" : "驳回退费(id=" + id + ")");

        return record;
    }

    private void processRefundApproval(RefundRecord record, BigDecimal refundAmount) {
        // 0. 取报名对应的课程ID（绕过逻辑删除：报名被删时 course_id 仍有效，退费需据此定位课时账户）
        Long courseId = enrollmentMapper.selectCourseIdById(record.getEnrollmentId());
        if (courseId == null) throw new BusinessException(404, "报名记录不存在");

        // 1. 金额上限校验：该 enrollment 累计缴费 - 累计已退费
        BigDecimal totalPaid = paymentRecordMapper.sumByEnrollmentId(record.getEnrollmentId());
        BigDecimal totalRefunded = refundRecordMapper.sumApprovedByEnrollmentId(record.getEnrollmentId());
        BigDecimal maxRefundable = totalPaid.subtract(totalRefunded);

        // 1.1 自动计算退费金额：remaining_lessons * (course.price / course.total_lessons)
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            LessonAccount account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                    .eq(LessonAccount::getStudentId, record.getStudentId())
                    .eq(LessonAccount::getCourseId, courseId));
            if (account != null) {
                Course course = courseMapper.selectById(account.getCourseId());
                if (course != null && course.getTotalLessons() != null && course.getTotalLessons() > 0
                        && course.getPrice() != null) {
                    BigDecimal pricePerLesson = course.getPrice()
                            .divide(BigDecimal.valueOf(course.getTotalLessons()), 4, java.math.RoundingMode.HALF_UP);
                    refundAmount = account.getRemainingLessons().multiply(pricePerLesson)
                            .setScale(2, java.math.RoundingMode.HALF_UP);
                    record.setAmount(refundAmount);
                }
            }
        }

        if (refundAmount == null) {
            refundAmount = BigDecimal.ZERO;
        }
        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "退费金额不能为负数");
        }
        if (refundAmount.compareTo(maxRefundable) > 0) {
            throw new BusinessException(409,
                    String.format("退费金额(%.2f)超过可退上限(%.2f)，超出部分需人工核实", refundAmount, maxRefundable));
        }

        // 2. 课时回退 — 乐观锁更新课时账户（MyBatis-Plus 自动处理 version+1）
        LessonAccount account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                .eq(LessonAccount::getStudentId, record.getStudentId())
                .eq(LessonAccount::getCourseId, courseId));
        if (account == null) {
            throw new BusinessException(404, "该学员无课时账户，无法退费");
        }

        BigDecimal refundLessonCount = record.getLessonCount() != null
                ? record.getLessonCount() : BigDecimal.ZERO;
        BigDecimal before = account.getRemainingLessons();
        if (before.compareTo(refundLessonCount) < 0) {
            throw new BusinessException(409, "剩余课时不足，无法完成退费课时回退");
        }

        account.setRemainingLessons(before.subtract(refundLessonCount));
        int rows = lessonAccountMapper.updateById(account);
        if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");

        // 3. 写入退费负向流水（sourceType=4 退费）
        LessonFlow flow = new LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(record.getStudentId());
        flow.setSourceType(4); // 退费
        flow.setSourceId(record.getId());
        flow.setChangeAmount(refundLessonCount.negate());
        flow.setChangeType(2);
        flow.setBeforeBalance(before);
        flow.setAfterBalance(account.getRemainingLessons());
        flow.setRemark("退费审核通过，回退课时");
        lessonFlowMapper.insert(flow);

        // 4. 联动将学员退班（从所有班级退出）
        classStudentMapper.update(null,
                new LambdaUpdateWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, record.getStudentId())
                        .set(ClassStudent::getStatus, 3));
    }

    // ==================== 课时账户 ====================

    @Override
    public Page<LessonAccount> pageLessonAccounts(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<LessonAccount> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, ACCOUNT_SORT_MAP, () -> wrapper.orderByDesc(LessonAccount::getId));
        Page<LessonAccount> page = lessonAccountMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateAccountNames(page.getRecords());
        return page;
    }

    private void populateAccountNames(List<LessonAccount> list) {
        if (list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(LessonAccount::getStudentId).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(LessonAccount::getCourseId).collect(Collectors.toSet());
        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
        for (LessonAccount a : list) {
            a.setStudentName(studentNames.getOrDefault(a.getStudentId(), ""));
            a.setCourseName(courseNames.getOrDefault(a.getCourseId(), ""));
        }
    }

    @Override
    public LessonAccount getLessonAccountById(Long id) {
        return lessonAccountMapper.selectById(id);
    }

    @Override
    public List<LessonAccount> getByStudentId(Long studentId) {
        List<LessonAccount> list = lessonAccountMapper.selectList(
                new LambdaQueryWrapper<LessonAccount>().eq(LessonAccount::getStudentId, studentId));
        populateAccountNames(list);
        return list;
    }

    @Override
    public Page<LessonFlow> pageLessonFlows(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<LessonFlow> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, FLOW_SORT_MAP, () -> wrapper.orderByDesc(LessonFlow::getCreateTime));
        Page<LessonFlow> page = lessonFlowMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateFlowNames(page.getRecords());
        return page;
    }

    private void populateFlowNames(List<LessonFlow> list) {
        if (list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(LessonFlow::getStudentId).collect(Collectors.toSet());
        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        for (LessonFlow f : list) {
            f.setStudentName(studentNames.getOrDefault(f.getStudentId(), ""));
        }
    }

    @Override
    public List<PaymentRecord> getPaymentsByStudentIds(List<Long> studentIds) {
        List<PaymentRecord> list = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .in(PaymentRecord::getStudentId, studentIds)
                        .orderByDesc(PaymentRecord::getPayTime));
        populatePaymentNames(list);
        return list;
    }

    @Override
    public List<LessonFlow> getFlowsByStudentId(Long studentId, int limit) {
        return lessonFlowMapper.selectList(
                new LambdaQueryWrapper<LessonFlow>().eq(LessonFlow::getStudentId, studentId)
                        .orderByDesc(LessonFlow::getCreateTime)
                        .last("LIMIT " + limit));
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
