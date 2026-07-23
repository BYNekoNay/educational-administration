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
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
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
        Set<Long> studentIds = list.stream().map(PaymentRecord::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(PaymentRecord::getCourseId).collect(Collectors.toSet());
        Map<Long, String> studentNames = loadStudentNamesIncludeDeleted(studentIds);
        Map<Long, String> courseNames = loadCourseNamesIncludeDeleted(courseIds);
        for (PaymentRecord p : list) {
            p.setStudentName(studentNames.getOrDefault(p.getStudentId(), ""));
            p.setCourseName(courseNames.getOrDefault(p.getCourseId(), ""));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PaymentRecord createPayment(PaymentRecord record) {
        if (record.getLessonCount() == null) throw new BusinessException(400, "课时数不能为空");
        if (record.getLessonCount().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "课时数必须为正数");
        if (record.getAmount() == null) throw new BusinessException(400, "缴费金额不能为空");
        // Bug #33 fix: 拒绝零金额缴费，防止免费赠送课时
        if (record.getAmount().compareTo(BigDecimal.ZERO) <= 0) throw new BusinessException(400, "缴费金额必须大于零");

        // 先校验报名状态，再写入缴费记录（Issue #6: 先校验后写入）
        Enrollment enrollment = enrollmentMapper.selectById(record.getEnrollmentId());
        if (enrollment == null) throw new BusinessException(404, "报名记录不存在");
        // L4 fix: null-safe 状态比较，防止 null 自动拆箱 NPE
        if (!Integer.valueOf(2).equals(enrollment.getStatus()) && !Integer.valueOf(3).equals(enrollment.getStatus())) {
            throw new BusinessException(409, "仅审核通过或已缴费的报名可缴费（续费），当前报名状态不可缴费");
        }

        // 强制从报名记录获取 studentId/courseId，防止前端传入错误值
        record.setStudentId(enrollment.getStudentId());
        record.setCourseId(enrollment.getCourseId());

        // Bug #10/#14 fix: CAS 原子更新报名状态作为第一个写操作，防止并发重复缴费
        // H1 fix: 续费时 status 已为 3，CAS 3→3 无效。增加 updateTime 条件使并发续费串行化
        int casRows = enrollmentMapper.update(null,
                new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getId, enrollment.getId())
                        .eq(Enrollment::getStatus, enrollment.getStatus())
                        .eq(enrollment.getUpdateTime() != null, Enrollment::getUpdateTime, enrollment.getUpdateTime())
                        .set(Enrollment::getStatus, 3)
                        .setSql("update_time = NOW()"));
        if (casRows == 0) {
            throw new BusinessException(409, "该报名状态已变更，请刷新重试");
        }
        enrollment.setStatus(3);

        paymentRecordMapper.insert(record);

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
            try {
                lessonAccountMapper.insert(account);
            } catch (DuplicateKeyException e) {
                // Bug #11 fix: 另一线程先创建了账户，改为查询并追加课时
                account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                        .eq(LessonAccount::getStudentId, record.getStudentId())
                        .eq(LessonAccount::getCourseId, record.getCourseId()));
                if (account == null) throw new BusinessException(500, "课时账户创建异常，请重试");
                beforeBalance = account.getRemainingLessons();
                account.setTotalLessons(account.getTotalLessons().add(record.getLessonCount()));
                account.setRemainingLessons(account.getRemainingLessons().add(record.getLessonCount()));
                int rows = lessonAccountMapper.updateById(account);
                if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");
            }
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

        if (enrollment.getClassId() != null) {
            ClassGroup classGroup = classGroupMapper.selectById(enrollment.getClassId());
            if (classGroup == null) throw new BusinessException(404, "所属班级不存在或已删除");
            // H2 fix: 先检查是否已有活跃记录（status=1），防止续费时重复插入
            ClassStudent activeRecord = classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, enrollment.getClassId())
                    .eq(ClassStudent::getStudentId, record.getStudentId())
                    .eq(ClassStudent::getStatus, 1));
            long currentCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getClassId, enrollment.getClassId())
                            .eq(ClassStudent::getStatus, 1));
            int maxCount = classGroup.getMaxStudentCount() != null ? classGroup.getMaxStudentCount() : 0;
            // M fix: 仅当学员尚无活跃座位（新入班/恢复）时才做容量预检。
            // currentCount 已含该学员自身的活跃行，满员时在班学员续费不占新座位，不应被拒。
            if (activeRecord == null && maxCount > 0 && currentCount >= maxCount) {
                throw new BusinessException(409, "班级已满，无法入班");
            }
            // 检查是否存在退费后的记录（status=3），若有则恢复为活跃状态
            if (activeRecord != null) {
                // 学员已在班级中，续费无需重复加入
            } else {
                ClassStudent refundedRecord = classStudentMapper.selectOne(new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, enrollment.getClassId())
                        .eq(ClassStudent::getStudentId, record.getStudentId())
                        .eq(ClassStudent::getStatus, 3));
                if (refundedRecord != null) {
                    refundedRecord.setStatus(1);
                    classStudentMapper.updateById(refundedRecord);
                } else {
                    ClassStudent cs = new ClassStudent();
                    cs.setClassId(enrollment.getClassId());
                    cs.setStudentId(record.getStudentId());
                    cs.setStatus(1);
                    classStudentMapper.insert(cs);
                }
            }
            // Bug #31 fix: 插入/恢复后再次校验班级人数，防止并发请求同时通过预检导致超员。
            // A5#6 fix: 仅在本次确实新增/恢复座位(activeRecord==null)时复检；在班学员续费不占新座位，
            // 班级已被调成超员时不应拒绝续费（与上方预检豁免一致）
            if (activeRecord == null && maxCount > 0) {
                long newCount = classStudentMapper.selectCount(
                        new LambdaQueryWrapper<ClassStudent>()
                                .eq(ClassStudent::getClassId, enrollment.getClassId())
                                .eq(ClassStudent::getStatus, 1));
                if (newCount > maxCount) {
                    throw new BusinessException(409, "班级已满，无法入班");
                }
            }
        }

        // 操作日志（M3 fix: 日志失败不回滚业务事务）
        try {
            operationLogService.log("财务管理", "登记收费（学员=" + nameResolver.getStudentName(record.getStudentId())
                    + "，金额=" + record.getAmount() + "，id=" + record.getId() + "）");
        } catch (Exception e) {
            log.warn("操作日志写入失败，不影响缴费业务", e);
        }

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
        Set<Long> studentIds = list.stream().map(RefundRecord::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> applicantIds = list.stream().map(RefundRecord::getApplicantId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> studentNames = loadStudentNamesIncludeDeleted(studentIds);
        // H3 fix: 空集合传入 selectBatchIds 会生成无效 SQL
        Map<Long, String> userNames = applicantIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(applicantIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
        for (RefundRecord r : list) {
            r.setStudentName(studentNames.getOrDefault(r.getStudentId(), ""));
            r.setApplicantName(userNames.getOrDefault(r.getApplicantId(), ""));
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefundRecord createRefund(RefundRecord record) {
        // Issue #2: 完整业务校验
        if (record.getEnrollmentId() == null) throw new BusinessException(400, "报名ID不能为空");
        if (record.getStudentId() == null) throw new BusinessException(400, "学员ID不能为空");

        Enrollment enrollment = enrollmentMapper.selectById(record.getEnrollmentId());
        if (enrollment == null) throw new BusinessException(404, "报名记录不存在");
        if (!Integer.valueOf(3).equals(enrollment.getStatus())) {
            throw new BusinessException(409, "仅已缴费的报名可申请退费");
        }
        // C1 fix: 强制使用 enrollment 的 studentId，防止请求体伪造导致扣错学员课时
        record.setStudentId(enrollment.getStudentId());

        // 校验是否已有待审核的退费申请
        // Bug #30 note: 此 check-then-insert 在极端并发下存在微小竞态窗口（两个请求同时通过检查）。
        // @Transactional(REPEATABLE_READ) 可缓解但不能完全消除。
        // 二次防线：auditRefund 中的 CAS 原子更新确保同一报名仅一条退费能被审核通过。
        Long pendingCount = refundRecordMapper.selectCount(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getEnrollmentId, record.getEnrollmentId())
                        .eq(RefundRecord::getStatus, 1));
        if (pendingCount > 0) {
            throw new BusinessException(409, "该报名已有待审核的退费申请，请勿重复提交");
        }

        if (record.getLessonCount() != null && record.getLessonCount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "退费课时数不能为负数");
        }
        // Low fix: 提交时若带金额，校验非负，避免待审核列表出现负数金额
        if (record.getAmount() != null && record.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "退费金额不能为负数");
        }

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

        // 同人隔离检测
        if (Objects.equals(record.getApplicantId(), auditorId)) {
            throw new BusinessException(409, "审核人与申请人不可为同一人，请转交其他财务人员复核");
        }

        BigDecimal finalAmount = null;

        if (status == 2) {
            // Bug #3 fix: 在状态更新之前执行超额校验和金额计算。
            // 此时 record 仍为 status=1，sumApprovedByEnrollmentId 不会包含当前记录，避免重复计入。
            finalAmount = validateAndCalculateRefund(record, refundAmount);
        }

        // Issue #5: 原子更新防止并发重复审核
        int updated = refundRecordMapper.update(null,
                new LambdaUpdateWrapper<RefundRecord>()
                        .eq(RefundRecord::getId, id)
                        .eq(RefundRecord::getStatus, 1)
                        .set(RefundRecord::getStatus, status)
                        .set(RefundRecord::getAuditorId, auditorId));
        if (updated == 0) {
            throw new BusinessException(409, "该退费申请已被处理，请刷新后重试");
        }
        // A5#2 fix: update(null,wrapper) 不突变内存对象，同步状态/审核人，避免返回陈旧的 status=1/auditorId=null
        record.setStatus(status);
        record.setAuditorId(auditorId);

        if (status == 2) {
            // 审核通过 — 执行退费操作（课时扣减、流水写入）
            executeRefundApproval(record, finalAmount);

            // Bug #2 fix: 无条件持久化最终退费金额（包括自动计算的情况）
            record.setAmount(finalAmount);
            refundRecordMapper.update(null,
                    new LambdaUpdateWrapper<RefundRecord>()
                            .eq(RefundRecord::getId, id)
                            .set(RefundRecord::getAmount, finalAmount));
        }

        // 操作日志（M3 fix: 日志失败不回滚业务事务）
        String studentName = nameResolver.getStudentName(record.getStudentId());
        try {
            operationLogService.log("财务管理", status == 2
                    ? "审核通过退费（学员=" + studentName + "，金额=" + finalAmount + "，id=" + id + "）"
                    : "驳回退费（学员=" + studentName + "，id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志写入失败，不影响退费业务", e);
        }

        return record;
    }

    /**
     * Bug #3 fix: 在记录状态仍为 1（待审核）时执行超额校验和金额计算。
     * 此时 sumApprovedByEnrollmentId 不包含当前记录，避免重复计入导致合法全额退费被拦截。
     *
     * @return 最终退费金额（显式指定或自动计算）
     */
    private BigDecimal validateAndCalculateRefund(RefundRecord record, BigDecimal refundAmount) {
        // 0. 取报名对应的课程ID（绕过逻辑删除）
        Long courseId = enrollmentMapper.selectCourseIdById(record.getEnrollmentId());
        if (courseId == null) throw new BusinessException(404, "报名记录不存在");

        // 1. 金额上限校验：该 enrollment 累计缴费 - 累计已退费（不含当前记录，因为当前记录仍为 status=1）
        BigDecimal totalPaid = paymentRecordMapper.sumByEnrollmentId(record.getEnrollmentId());
        BigDecimal totalRefunded = refundRecordMapper.sumApprovedByEnrollmentId(record.getEnrollmentId());
        BigDecimal maxRefundable = totalPaid.subtract(totalRefunded);

        // 获取课时账户
        LessonAccount account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                .eq(LessonAccount::getStudentId, record.getStudentId())
                .eq(LessonAccount::getCourseId, courseId));
        if (account == null) {
            throw new BusinessException(404, "该学员无课时账户，无法退费");
        }

        // Issue #3: 基于实际缴费单价计算退费金额（替代课程原价）
        // Bug #29 fix: 使用累计购买课时数（不随退费变化）作为分母，避免部分退费后分母缩小导致单价虚高
        BigDecimal originalTotalLessons = paymentRecordMapper.sumLessonCountByEnrollmentId(record.getEnrollmentId());
        BigDecimal pricePerLesson = BigDecimal.ZERO;
        if (originalTotalLessons != null && originalTotalLessons.compareTo(BigDecimal.ZERO) > 0
                && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            pricePerLesson = totalPaid.divide(originalTotalLessons, 4, java.math.RoundingMode.HALF_UP);
        }

        // 自动计算退费金额（审核人未显式指定时）
        // Issue #4 fix: 与家长端 ParentRefundController 保持一致，采用单步比例公式
        // totalPaid × remain / originalTotal（scale 2, HALF_UP），避免两步法（先算 4 位单价再相乘）的舍入偏差
        if (refundAmount == null || refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            if (originalTotalLessons != null && originalTotalLessons.compareTo(BigDecimal.ZERO) > 0
                    && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
                refundAmount = totalPaid.multiply(account.getRemainingLessons())
                        .divide(originalTotalLessons, 2, java.math.RoundingMode.HALF_UP);
            } else {
                refundAmount = BigDecimal.ZERO;
            }
        }

        if (refundAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "退费金额不能为负数");
        }
        if (refundAmount.compareTo(maxRefundable) > 0) {
            throw new BusinessException(409,
                    String.format("退费金额(%.2f)超过可退上限(%.2f)，超出部分需人工核实", refundAmount, maxRefundable));
        }

        // Issue #1 fix: 退费金额不得超过剩余课时价值，防止超额退费
        // Medium fix: 改用与自动计算一致的单步比例公式，避免两步法（4 位单价再相乘）舍入导致
        // 合法全额退费被误拒（如 99.99 < 100.00）
        BigDecimal lessonValueCap = BigDecimal.ZERO;
        if (originalTotalLessons != null && originalTotalLessons.compareTo(BigDecimal.ZERO) > 0
                && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            lessonValueCap = totalPaid.multiply(account.getRemainingLessons())
                    .divide(originalTotalLessons, 2, java.math.RoundingMode.HALF_UP);
        }
        if (refundAmount.compareTo(lessonValueCap) > 0) {
            throw new BusinessException(409, "退费金额超过剩余课时价值(" + lessonValueCap + ")");
        }

        return refundAmount;
    }

    /**
     * 审核通过后执行退费操作：课时扣减、流水写入、报名状态更新。
     * 调用前 record 状态已通过 CAS 更新为 2（已审核通过）。
     */
    private void executeRefundApproval(RefundRecord record, BigDecimal finalAmount) {
        Long courseId = enrollmentMapper.selectCourseIdById(record.getEnrollmentId());
        if (courseId == null) throw new BusinessException(404, "报名记录不存在");

        BigDecimal totalPaid = paymentRecordMapper.sumByEnrollmentId(record.getEnrollmentId());

        // 获取课时账户
        LessonAccount account = lessonAccountMapper.selectOne(new LambdaQueryWrapper<LessonAccount>()
                .eq(LessonAccount::getStudentId, record.getStudentId())
                .eq(LessonAccount::getCourseId, courseId));
        if (account == null) {
            throw new BusinessException(404, "该学员无课时账户，无法退费");
        }

        // Issue #3 fix: 剩余课时为 0 时直接拒绝，避免 0 课时扣减的空操作退费
        if (account.getRemainingLessons().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "剩余课时为0，无可退费课时");
        }

        // 计算单价
        // Bug #29 fix: 使用累计购买课时数（不随退费变化）作为分母，避免部分退费后分母缩小导致单价虚高
        BigDecimal originalTotalLessons = paymentRecordMapper.sumLessonCountByEnrollmentId(record.getEnrollmentId());
        BigDecimal pricePerLesson = BigDecimal.ZERO;
        if (originalTotalLessons != null && originalTotalLessons.compareTo(BigDecimal.ZERO) > 0
                && totalPaid.compareTo(BigDecimal.ZERO) > 0) {
            pricePerLesson = totalPaid.divide(originalTotalLessons, 4, java.math.RoundingMode.HALF_UP);
        }

        // Issue #4: lessonCount 为 null 时根据退费金额反算课时数
        BigDecimal refundLessonCount;
        if (record.getLessonCount() != null && record.getLessonCount().compareTo(BigDecimal.ZERO) > 0) {
            // M2 fix: 提交时的 lessonCount 可能已过期（期间有考勤消耗），上限为当前剩余
            refundLessonCount = record.getLessonCount().min(account.getRemainingLessons());
        } else if (pricePerLesson.compareTo(BigDecimal.ZERO) > 0 && finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            // A5#3 fix: 反算课时数用高精度(scale=10)，避免 2 位小数舍入误差(最高 0.005×单价)超过下方 0.01 容差，
            // 误拒合法退费（如 50元/单价150 → 0.33课时×150=49.50 < 50 触发假 409）
            refundLessonCount = finalAmount.divide(pricePerLesson, 10, java.math.RoundingMode.HALF_UP);
            // 不超过剩余课时
            if (refundLessonCount.compareTo(account.getRemainingLessons()) > 0) {
                refundLessonCount = account.getRemainingLessons();
            }
        } else if (finalAmount.compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException(400, "无法自动计算退费课时数，请手动填写 lessonCount");
        } else {
            refundLessonCount = BigDecimal.ZERO;
        }

        // High fix: 退费金额不得超过"实际回退课时"的价值，防止"退全款只扣 1 课时"的超额退费。
        // validateAndCalculateRefund 的上限是按全部剩余课时计算的，此处按真正回退的课时数二次校验。
        BigDecimal refundValueCap = refundLessonCount.multiply(pricePerLesson)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        if (finalAmount.compareTo(refundValueCap.add(new BigDecimal("0.01"))) > 0) {
            throw new BusinessException(409,
                    String.format("退费金额(%.2f)超过回退课时价值(%.2f)，请调整退费课时数或金额", finalAmount, refundValueCap));
        }

        BigDecimal before = account.getRemainingLessons();
        if (before.compareTo(refundLessonCount) < 0) {
            throw new BusinessException(409, "剩余课时不足，无法完成退费课时回退");
        }

        account.setRemainingLessons(before.subtract(refundLessonCount));
        account.setTotalLessons(account.getTotalLessons().subtract(refundLessonCount)); // 同步扣减 totalLessons
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

        // 4. 全额退费时将报名状态更新为已退费(6)，防止重复退费
        if (account.getRemainingLessons().compareTo(BigDecimal.ZERO) <= 0) {
            enrollmentMapper.update(null,
                    new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getId, record.getEnrollmentId())
                            .set(Enrollment::getStatus, 6));
            Set<Long> courseClassIds = classGroupMapper.selectList(
                    new LambdaQueryWrapper<ClassGroup>()
                            .eq(ClassGroup::getCourseId, courseId))
                    .stream()
                    .map(ClassGroup::getId)
                    .collect(Collectors.toSet());

            if (!courseClassIds.isEmpty()) {
                classStudentMapper.update(null,
                        new LambdaUpdateWrapper<ClassStudent>()
                                .eq(ClassStudent::getStudentId, record.getStudentId())
                                .in(ClassStudent::getClassId, courseClassIds)
                                .eq(ClassStudent::getStatus, 1) // 仅影响活跃状态的报名记录
                                .set(ClassStudent::getStatus, 3));
            }
        }
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
        Set<Long> studentIds = list.stream().map(LessonAccount::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(LessonAccount::getCourseId).collect(Collectors.toSet());
        Map<Long, String> studentNames = loadStudentNamesIncludeDeleted(studentIds);
        Map<Long, String> courseNames = loadCourseNamesIncludeDeleted(courseIds);
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
        Set<Long> studentIds = list.stream().map(LessonFlow::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        if (studentIds.isEmpty()) return;
        Map<Long, String> studentNames = loadStudentNamesIncludeDeleted(studentIds);
        for (LessonFlow f : list) {
            f.setStudentName(studentNames.getOrDefault(f.getStudentId(), ""));
        }
    }

    /**
     * 加载学员姓名映射（绕过 @TableLogic，包含已逻辑删除的学员）。
     *
     * <p>用于历史流水/退费/收费/课时账户等可能引用已删学员的页面。
     * 历史记录必须保留当时姓名，不能因学员被软删而消失。</p>
     */
    private Map<Long, String> loadStudentNamesIncludeDeleted(Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return Collections.emptyMap();
        List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(studentIds);
        return raw.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> (String) m.get("name"),
                        (a, b) -> a));
    }

    /** 绕过 @TableLogic 加载课程名映射（含已软删课程） */
    private Map<Long, String> loadCourseNamesIncludeDeleted(Set<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) return Collections.emptyMap();
        List<Map<String, Object>> raw = courseMapper.selectNamesByIdsIncludeDeleted(courseIds);
        return raw.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> (String) m.get("name"),
                        (a, b) -> a));
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
        Page<LessonFlow> page = lessonFlowMapper.selectPage(
                new Page<>(1, limit),
                new LambdaQueryWrapper<LessonFlow>().eq(LessonFlow::getStudentId, studentId)
                        .orderByDesc(LessonFlow::getCreateTime));
        return page.getRecords();
    }
}
