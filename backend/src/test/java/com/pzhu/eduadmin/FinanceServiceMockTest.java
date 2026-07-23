package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.*;
import com.pzhu.eduadmin.modules.finance.mapper.*;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.finance.service.FinanceServiceImpl;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("财务服务单元测试")
class FinanceServiceMockTest {

    @Mock private PaymentRecordMapper paymentMapper;
    @Mock private RefundRecordMapper refundMapper;
    @Mock private LessonAccountMapper accountMapper;
    @Mock private LessonFlowMapper flowMapper;
    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private CourseMapper courseMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private FinanceServiceImpl financeService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, RefundRecord.class);
        TableInfoHelper.initTableInfo(assistant, LessonAccount.class);
        TableInfoHelper.initTableInfo(assistant, LessonFlow.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ClassGroup.class);
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
    }

    @BeforeEach
    void setUp() { CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN")); }

    @AfterEach
    void tearDown() { CurrentUserHolder.clear(); }

    // ==================== Helper Methods ====================

    private Enrollment buildEnrollment(Long id, Long studentId, Long courseId, int status, Long classId) {
        Enrollment e = new Enrollment();
        e.setId(id);
        e.setStudentId(studentId);
        e.setCourseId(courseId);
        e.setStatus(status);
        e.setClassId(classId);
        return e;
    }

    private PaymentRecord buildPaymentRecord(Long enrollmentId, BigDecimal lessonCount, BigDecimal amount) {
        PaymentRecord p = new PaymentRecord();
        p.setEnrollmentId(enrollmentId);
        p.setLessonCount(lessonCount);
        p.setAmount(amount);
        p.setRemark("test");
        return p;
    }

    // ==================== createPayment Tests ====================

    @Nested
    @DisplayName("createPayment - 登记收费")
    class CreatePaymentTests {

        @Test
        @DisplayName("正常缴费 - 新建课时账户，无班级分配")
        void shouldCreatePayment_newAccount_noClass() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(null);
            doAnswer(inv -> { inv.getArgument(0, LessonAccount.class).setId(50L); return 1; })
                    .when(accountMapper).insert(any(LessonAccount.class));
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            assertThat(result.getStudentId()).isEqualTo(10L);
            assertThat(result.getCourseId()).isEqualTo(20L);

            verify(accountMapper).insert(any(LessonAccount.class));
            verify(flowMapper).insert(any(LessonFlow.class));
            verify(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            assertThat(enrollment.getStatus()).isEqualTo(3);
        }

        @Test
        @DisplayName("正常缴费 - 已有课时账户，追加课时")
        void shouldCreatePayment_existingAccount() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("5"), new BigDecimal("500"));

            LessonAccount existingAccount = new LessonAccount();
            existingAccount.setId(50L);
            existingAccount.setTotalLessons(new BigDecimal("20"));
            existingAccount.setRemainingLessons(new BigDecimal("15"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(101L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(existingAccount);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            assertThat(result).isNotNull();
            assertThat(existingAccount.getTotalLessons()).isEqualByComparingTo(new BigDecimal("25"));
            assertThat(existingAccount.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("20"));

            verify(accountMapper, never()).insert(any(LessonAccount.class));
            verify(accountMapper).updateById(any(LessonAccount.class));
        }

        @Test
        @DisplayName("正常缴费 - 含班级分配，新入班")
        void shouldCreatePayment_withClassEnrollment() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, 5L);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            ClassGroup classGroup = new ClassGroup();
            classGroup.setId(5L);
            classGroup.setMaxStudentCount(30);

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(null);
            doAnswer(inv -> { inv.getArgument(0, LessonAccount.class).setId(50L); return 1; })
                    .when(accountMapper).insert(any(LessonAccount.class));
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(classGroupMapper.selectById(5L)).thenReturn(classGroup);
            when(classStudentMapper.selectCount(any())).thenReturn(0L);
            when(classStudentMapper.selectOne(any())).thenReturn(null);
            doReturn(1).when(classStudentMapper).insert(any(ClassStudent.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            assertThat(result).isNotNull();
            verify(classStudentMapper).insert(any(ClassStudent.class));
            // cross-validation: studentId/courseId forced from enrollment
            assertThat(record.getStudentId()).isEqualTo(10L);
            assertThat(record.getCourseId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("正常缴费 - 班级中有退费记录，恢复为活跃状态")
        void shouldCreatePayment_restoreRefundedClassRecord() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, 5L);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            ClassGroup classGroup = new ClassGroup();
            classGroup.setId(5L);
            classGroup.setMaxStudentCount(30);

            ClassStudent refundedRecord = new ClassStudent();
            refundedRecord.setId(77L);
            refundedRecord.setStatus(3);

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(null);
            doAnswer(inv -> { inv.getArgument(0, LessonAccount.class).setId(50L); return 1; })
                    .when(accountMapper).insert(any(LessonAccount.class));
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(classGroupMapper.selectById(5L)).thenReturn(classGroup);
            when(classStudentMapper.selectCount(any())).thenReturn(0L);
            // H2 fix: 第一次 selectOne 查活跃记录返回 null，第二次查退费记录返回 refundedRecord
            when(classStudentMapper.selectOne(any())).thenReturn(null).thenReturn(refundedRecord);
            when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            financeService.createPayment(record);

            // Should restore refunded record (status 3 -> 1) instead of inserting new
            verify(classStudentMapper, never()).insert(any(ClassStudent.class));
            verify(classStudentMapper).updateById(refundedRecord);
            assertThat(refundedRecord.getStatus()).isEqualTo(1);
        }

        @Test
        @DisplayName("报名记录不存在应抛异常")
        void shouldThrowWhenEnrollmentNotFound() {
            PaymentRecord record = buildPaymentRecord(999L, new BigDecimal("10"), new BigDecimal("1000"));
            when(enrollmentMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("报名记录不存在");
        }

        @Test
        @DisplayName("报名状态为待审核(1)不可缴费")
        void shouldThrowWhenEnrollmentStatusPending() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 1, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可缴费");
        }

        @Test
        @DisplayName("报名状态为已拒绝(4)不可缴费")
        void shouldThrowWhenEnrollmentStatusRejected() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 4, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("不可缴费");
        }

        @Test
        @DisplayName("强制从报名记录获取studentId/courseId（交叉校验）")
        void shouldCrossValidateStudentAndCourseFromEnrollment() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));
            // Frontend sends wrong IDs
            record.setStudentId(999L);
            record.setCourseId(888L);

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(null);
            doAnswer(inv -> { inv.getArgument(0, LessonAccount.class).setId(50L); return 1; })
                    .when(accountMapper).insert(any(LessonAccount.class));
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            // IDs should be overwritten from enrollment
            assertThat(result.getStudentId()).isEqualTo(10L);
            assertThat(result.getCourseId()).isEqualTo(20L);
        }

        @Test
        @DisplayName("课时数为空应抛异常")
        void shouldThrowWhenLessonCountNull() {
            PaymentRecord record = new PaymentRecord();
            record.setEnrollmentId(1L);
            record.setAmount(new BigDecimal("1000"));
            // lessonCount is null

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("课时数不能为空");
        }

        @Test
        @DisplayName("课时数为零应抛异常")
        void shouldThrowWhenLessonCountZero() {
            PaymentRecord record = new PaymentRecord();
            record.setEnrollmentId(1L);
            record.setLessonCount(BigDecimal.ZERO);
            record.setAmount(new BigDecimal("1000"));

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("课时数必须为正数");
        }

        @Test
        @DisplayName("金额为空应抛异常")
        void shouldThrowWhenAmountNull() {
            PaymentRecord record = new PaymentRecord();
            record.setEnrollmentId(1L);
            record.setLessonCount(new BigDecimal("10"));
            // amount is null

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缴费金额不能为空");
        }

        @Test
        @DisplayName("金额为负数应抛异常")
        void shouldThrowWhenAmountNegative() {
            PaymentRecord record = new PaymentRecord();
            record.setEnrollmentId(1L);
            record.setLessonCount(new BigDecimal("10"));
            record.setAmount(new BigDecimal("-100"));

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("缴费金额必须大于零");
        }

        @Test
        @DisplayName("班级已满应抛异常")
        void shouldThrowWhenClassFull() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, 5L);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            ClassGroup classGroup = new ClassGroup();
            classGroup.setId(5L);
            classGroup.setMaxStudentCount(2);

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(null);
            doAnswer(inv -> { inv.getArgument(0, LessonAccount.class).setId(50L); return 1; })
                    .when(accountMapper).insert(any(LessonAccount.class));
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(classGroupMapper.selectById(5L)).thenReturn(classGroup);
            when(classStudentMapper.selectCount(any())).thenReturn(2L);

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("班级已满");
        }

        @Test
        @DisplayName("已缴费状态(3)的报名可续费")
        void shouldAllowRenewalForAlreadyPaidEnrollment() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 3, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("5"), new BigDecimal("500"));

            LessonAccount existingAccount = new LessonAccount();
            existingAccount.setId(50L);
            existingAccount.setTotalLessons(new BigDecimal("10"));
            existingAccount.setRemainingLessons(new BigDecimal("8"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(200L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            when(accountMapper.selectOne(any())).thenReturn(existingAccount);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(200L);
            assertThat(existingAccount.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("13"));
        }

        @Test
        @DisplayName("Bug#10/#14 - 并发缴费CAS失败应抛异常（报名状态已变更）")
        void shouldThrowWhenCasUpdateFails() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            // CAS update returns 0 → concurrent modification detected
            doReturn(0).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));

            assertThatThrownBy(() -> financeService.createPayment(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("该报名状态已变更");

            // Payment record should NOT be inserted
            verify(paymentMapper, never()).insert(any(PaymentRecord.class));
        }

        @Test
        @DisplayName("Bug#11 - 课时账户并发插入冲突时回退到更新路径")
        void shouldFallbackToUpdateOnDuplicateKey() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            PaymentRecord record = buildPaymentRecord(1L, new BigDecimal("10"), new BigDecimal("1000"));

            LessonAccount existingAccount = new LessonAccount();
            existingAccount.setId(50L);
            existingAccount.setStudentId(10L);
            existingAccount.setCourseId(20L);
            existingAccount.setTotalLessons(new BigDecimal("20"));
            existingAccount.setRemainingLessons(new BigDecimal("15"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            doReturn(1).when(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
            doAnswer(inv -> { inv.getArgument(0, PaymentRecord.class).setId(100L); return 1; })
                    .when(paymentMapper).insert(any(PaymentRecord.class));
            // First selectOne returns null (account doesn't exist yet)
            // After DuplicateKeyException, second selectOne returns the account created by another thread
            when(accountMapper.selectOne(any())).thenReturn(null).thenReturn(existingAccount);
            // Insert throws DuplicateKeyException (another thread created it first)
            doThrow(new DuplicateKeyException("Duplicate entry"))
                    .when(accountMapper).insert(any(LessonAccount.class));
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            PaymentRecord result = financeService.createPayment(record);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(100L);
            // Should have fallen through to update path: 15 + 10 = 25
            assertThat(existingAccount.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("25"));
            assertThat(existingAccount.getTotalLessons()).isEqualByComparingTo(new BigDecimal("30"));
            verify(accountMapper).updateById(any(LessonAccount.class));
        }
    }

    // ==================== createRefund Tests ====================

    @Nested
    @DisplayName("createRefund - 申请退费")
    class CreateRefundTests {

        @Test
        @DisplayName("正常创建退费申请 - 状态设为待审核(1)")
        void shouldCreateRefund_success() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 3, null);
            RefundRecord record = new RefundRecord();
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setAmount(new BigDecimal("500"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            when(refundMapper.selectCount(any())).thenReturn(0L);
            doAnswer(inv -> { inv.getArgument(0, RefundRecord.class).setId(200L); return 1; })
                    .when(refundMapper).insert(any(RefundRecord.class));

            RefundRecord result = financeService.createRefund(record);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(200L);
            assertThat(result.getStatus()).isEqualTo(1);
            verify(refundMapper).insert(any(RefundRecord.class));
        }

        @Test
        @DisplayName("报名ID为空应抛异常")
        void shouldThrowWhenEnrollmentIdNull() {
            RefundRecord record = new RefundRecord();
            record.setStudentId(10L);

            assertThatThrownBy(() -> financeService.createRefund(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("报名ID不能为空");
        }

        @Test
        @DisplayName("学员ID为空应抛异常")
        void shouldThrowWhenStudentIdNull() {
            RefundRecord record = new RefundRecord();
            record.setEnrollmentId(1L);

            assertThatThrownBy(() -> financeService.createRefund(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("学员ID不能为空");
        }

        @Test
        @DisplayName("报名记录不存在应抛异常")
        void shouldThrowWhenEnrollmentNotFound() {
            RefundRecord record = new RefundRecord();
            record.setEnrollmentId(999L);
            record.setStudentId(10L);

            when(enrollmentMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() -> financeService.createRefund(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("报名记录不存在");
        }

        @Test
        @DisplayName("报名未缴费(status=2)不可申请退费")
        void shouldThrowWhenEnrollmentNotPaid() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 2, null);
            RefundRecord record = new RefundRecord();
            record.setEnrollmentId(1L);
            record.setStudentId(10L);

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);

            assertThatThrownBy(() -> financeService.createRefund(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("仅已缴费的报名可申请退费");
        }

        @Test
        @DisplayName("已有待审核退费申请不可重复提交")
        void shouldThrowWhenDuplicatePendingRefund() {
            Enrollment enrollment = buildEnrollment(1L, 10L, 20L, 3, null);
            RefundRecord record = new RefundRecord();
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setAmount(new BigDecimal("500"));

            when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
            when(refundMapper.selectCount(any())).thenReturn(1L);

            assertThatThrownBy(() -> financeService.createRefund(record))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已有待审核的退费申请");
        }
    }

    // ==================== auditRefund Tests ====================

    @Nested
    @DisplayName("auditRefund - 退费审核")
    class AuditRefundTests {

        @Test
        @DisplayName("审核通过 - 原子更新状态并恢复课时账户余额")
        void shouldApproveRefund_restoreBalance() {
            RefundRecord record = new RefundRecord();
            record.setId(1L);
            record.setStatus(1);
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setApplicantId(2L);
            record.setLessonCount(new BigDecimal("5"));

            LessonAccount account = new LessonAccount();
            account.setId(50L);
            account.setStudentId(10L);
            account.setTotalLessons(new BigDecimal("20"));
            account.setRemainingLessons(new BigDecimal("10"));

            when(refundMapper.selectById(1L)).thenReturn(record);
            when(refundMapper.update(any(), any())).thenReturn(1);
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(20L);
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2000"));
            when(paymentMapper.sumLessonCountByEnrollmentId(1L)).thenReturn(new BigDecimal("20"));
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountMapper.selectOne(any())).thenReturn(account);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            lenient().when(classGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            RefundRecord result = financeService.auditRefund(1L, 2, 4L, new BigDecimal("500"));

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            // Remaining: 10 - 5 = 5
            assertThat(account.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("5"));
            verify(flowMapper).insert(any(LessonFlow.class));
            verify(accountMapper).updateById(any(LessonAccount.class));
        }

        @Test
        @DisplayName("审核通过 - 退费金额超上限应抛异常")
        void shouldRejectExcessRefund() {
            RefundRecord record = new RefundRecord();
            record.setId(1L);
            record.setStatus(1);
            record.setEnrollmentId(1L);
            record.setStudentId(1L);
            record.setApplicantId(1L);

            LessonAccount account = new LessonAccount();
            account.setId(1L);
            account.setTotalLessons(new BigDecimal("40"));
            account.setRemainingLessons(new BigDecimal("20"));

            when(refundMapper.selectById(1L)).thenReturn(record);
            // Bug #3 fix: validation now runs BEFORE CAS update, so refundMapper.update is never reached
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2400"));
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(1L);
            when(accountMapper.selectOne(any())).thenReturn(account);

            assertThatThrownBy(() ->
                    financeService.auditRefund(1L, 2, 4L, new BigDecimal("99999")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("超过");
        }

        @Test
        @DisplayName("审核人与申请人为同一人应拒绝")
        void shouldRejectSamePersonAudit() {
            RefundRecord record = new RefundRecord();
            record.setId(2L);
            record.setStatus(1);
            record.setApplicantId(4L);

            when(refundMapper.selectById(2L)).thenReturn(record);

            assertThatThrownBy(() ->
                    financeService.auditRefund(2L, 2, 4L, BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("同一人");
        }

        @Test
        @DisplayName("退费记录不存在应抛异常")
        void shouldThrowWhenRefundNotFound() {
            when(refundMapper.selectById(999L)).thenReturn(null);

            assertThatThrownBy(() ->
                    financeService.auditRefund(999L, 2, 4L, BigDecimal.ZERO))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("退费记录不存在");
        }

        @Test
        @DisplayName("并发审核 - 原子更新返回0应抛异常")
        void shouldThrowWhenConcurrentAudit() {
            RefundRecord record = new RefundRecord();
            record.setId(5L);
            record.setStatus(1);
            record.setApplicantId(2L);
            record.setEnrollmentId(1L);
            record.setStudentId(10L);

            LessonAccount account = new LessonAccount();
            account.setId(50L);
            account.setTotalLessons(new BigDecimal("20"));
            account.setRemainingLessons(new BigDecimal("10"));

            when(refundMapper.selectById(5L)).thenReturn(record);
            // Bug #3 fix: validation runs before CAS, so provide validation mocks
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(20L);
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2000"));
            when(paymentMapper.sumLessonCountByEnrollmentId(1L)).thenReturn(new BigDecimal("20"));
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountMapper.selectOne(any())).thenReturn(account);
            // CAS update returns 0 → concurrent audit detected
            when(refundMapper.update(any(), any())).thenReturn(0);

            assertThatThrownBy(() ->
                    financeService.auditRefund(5L, 2, 4L, new BigDecimal("500")))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("已被处理");
        }

        @Test
        @DisplayName("审核拒绝 - 仅更新状态和审核人，不触发课时回退")
        void shouldRejectRefund() {
            RefundRecord record = new RefundRecord();
            record.setId(3L);
            record.setStatus(1);
            record.setStudentId(10L);
            record.setApplicantId(1L);

            when(refundMapper.selectById(3L)).thenReturn(record);
            when(refundMapper.update(any(), any())).thenReturn(1);
            lenient().when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            RefundRecord result = financeService.auditRefund(3L, 3, 4L, BigDecimal.ZERO);

            verify(refundMapper).update(any(), any());
            verify(accountMapper, never()).updateById(any(LessonAccount.class));
            verify(flowMapper, never()).insert(any(LessonFlow.class));
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(3L);
        }

        @Test
        @DisplayName("审核通过 - 课时归零时学员从班级退出")
        void shouldApproveRefund_removeFromClassWhenLessonsZero() {
            RefundRecord record = new RefundRecord();
            record.setId(10L);
            record.setStatus(1);
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setApplicantId(2L);
            record.setLessonCount(new BigDecimal("10"));

            LessonAccount account = new LessonAccount();
            account.setId(50L);
            account.setStudentId(10L);
            account.setTotalLessons(new BigDecimal("10"));
            account.setRemainingLessons(new BigDecimal("10"));

            ClassGroup classGroup = new ClassGroup();
            classGroup.setId(5L);
            classGroup.setCourseId(20L);

            when(refundMapper.selectById(10L)).thenReturn(record);
            when(refundMapper.update(any(), any())).thenReturn(1);
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(20L);
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("1000"));
            when(paymentMapper.sumLessonCountByEnrollmentId(1L)).thenReturn(new BigDecimal("10"));
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountMapper.selectOne(any())).thenReturn(account);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            when(classGroupMapper.selectList(any())).thenReturn(List.of(classGroup));
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            financeService.auditRefund(10L, 2, 4L, new BigDecimal("1000"));

            // Remaining: 10 - 10 = 0, should trigger class removal
            assertThat(account.getRemainingLessons()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(classStudentMapper).update(any(), any());
        }

        @Test
        @DisplayName("Bug#2 - 自动计算的退费金额必须持久化到DB（审核人未指定金额时）")
        void shouldPersistAutoCalculatedAmount() {
            RefundRecord record = new RefundRecord();
            record.setId(20L);
            record.setStatus(1);
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setApplicantId(2L);
            // High fix 后退费金额不得超过回退课时价值：自动计算金额=1000（10 课时×100），
            // 故 lessonCount 须为 10（回退全部剩余课时），否则触发超额退费拦截
            record.setLessonCount(new BigDecimal("10"));

            LessonAccount account = new LessonAccount();
            account.setId(50L);
            account.setStudentId(10L);
            account.setTotalLessons(new BigDecimal("20"));
            account.setRemainingLessons(new BigDecimal("10"));

            when(refundMapper.selectById(20L)).thenReturn(record);
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(20L);
            // totalPaid=2000, totalLessons=20 → pricePerLesson=100
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2000"));
            when(paymentMapper.sumLessonCountByEnrollmentId(1L)).thenReturn(new BigDecimal("20"));
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountMapper.selectOne(any())).thenReturn(account);
            when(refundMapper.update(any(), any())).thenReturn(1);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            lenient().when(classGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            // Auditor passes BigDecimal.ZERO → auto-calculate: remainingLessons(10) * pricePerLesson(100) = 1000
            RefundRecord result = financeService.auditRefund(20L, 2, 4L, BigDecimal.ZERO);

            // Bug #2 fix: auto-calculated amount (1000) must be set on record and persisted
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
            // Verify amount persistence update was called (CAS update + amount update = 2 calls)
            verify(refundMapper, times(2)).update(any(), any());
        }

        @Test
        @DisplayName("Bug#3 - 全额退费不被当前记录重复计入所阻塞")
        void shouldAllowFullRefund_withoutDoubleCounting() {
            // Scenario: totalPaid=2000, no prior approved refunds.
            // The current record's amount should NOT be included in totalRefunded
            // because validation runs while record is still status=1.
            RefundRecord record = new RefundRecord();
            record.setId(30L);
            record.setStatus(1);
            record.setEnrollmentId(1L);
            record.setStudentId(10L);
            record.setApplicantId(2L);
            record.setLessonCount(new BigDecimal("20"));

            LessonAccount account = new LessonAccount();
            account.setId(50L);
            account.setStudentId(10L);
            account.setTotalLessons(new BigDecimal("20"));
            account.setRemainingLessons(new BigDecimal("20"));

            when(refundMapper.selectById(30L)).thenReturn(record);
            when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(20L);
            when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2000"));
            when(paymentMapper.sumLessonCountByEnrollmentId(1L)).thenReturn(new BigDecimal("20"));
            // Bug #3: sumApprovedByEnrollmentId returns 0 because current record is still status=1
            // (validation runs BEFORE CAS update). If it incorrectly returned 2000 (including
            // the current record), maxRefundable would be 0 and the refund would be blocked.
            when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
            when(accountMapper.selectOne(any())).thenReturn(account);
            when(refundMapper.update(any(), any())).thenReturn(1);
            when(accountMapper.updateById(any(LessonAccount.class))).thenReturn(1);
            doReturn(1).when(flowMapper).insert(any(LessonFlow.class));
            lenient().when(classGroupMapper.selectList(any())).thenReturn(Collections.emptyList());
            when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");

            // Full refund of 2000 should succeed (maxRefundable = 2000 - 0 = 2000)
            RefundRecord result = financeService.auditRefund(30L, 2, 4L, new BigDecimal("2000"));

            assertThat(result).isNotNull();
            assertThat(result.getAmount()).isEqualByComparingTo(new BigDecimal("2000"));
            assertThat(account.getRemainingLessons()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(flowMapper).insert(any(LessonFlow.class));
        }
    }

    // ==================== Pagination Tests ====================

    @Nested
    @DisplayName("分页查询")
    class PaginationTests {

        @Test
        @DisplayName("pagePaymentRecords - 返回Page对象")
        void shouldReturnPaymentPage() {
            Page<PaymentRecord> expectedPage = new Page<>(1, 10);
            expectedPage.setRecords(Collections.emptyList());

            when(paymentMapper.selectPage(any(Page.class), any())).thenReturn(expectedPage);

            Page<PaymentRecord> result = financeService.pagePaymentRecords(1, 10, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
            verify(paymentMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("pageRefundRecords - 返回Page对象")
        void shouldReturnRefundPage() {
            Page<RefundRecord> expectedPage = new Page<>(1, 10);
            expectedPage.setRecords(Collections.emptyList());

            when(refundMapper.selectPage(any(Page.class), any())).thenReturn(expectedPage);

            Page<RefundRecord> result = financeService.pageRefundRecords(1, 10, null, null);

            assertThat(result).isNotNull();
            assertThat(result.getRecords()).isEmpty();
            verify(refundMapper).selectPage(any(Page.class), any());
        }

        @Test
        @DisplayName("pagePaymentRecords - 含记录时填充学员和课程名称")
        void shouldPopulatePaymentNames() {
            PaymentRecord p = new PaymentRecord();
            p.setId(1L);
            p.setStudentId(10L);
            p.setCourseId(20L);

            Page<PaymentRecord> expectedPage = new Page<>(1, 10);
            expectedPage.setRecords(List.of(p));

            when(paymentMapper.selectPage(any(Page.class), any())).thenReturn(expectedPage);
            when(studentMapper.selectNamesByIdsIncludeDeleted(any()))
                    .thenReturn(List.of(java.util.Map.of("id", 10L, "name", "张三")));
            when(courseMapper.selectNamesByIdsIncludeDeleted(any()))
                    .thenReturn(List.of(java.util.Map.of("id", 20L, "name", "钢琴课")));

            Page<PaymentRecord> result = financeService.pagePaymentRecords(1, 10, null, null);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("张三");
            assertThat(result.getRecords().get(0).getCourseName()).isEqualTo("钢琴课");
        }

        @Test
        @DisplayName("pageRefundRecords - 含记录时填充学员和申请人名称")
        void shouldPopulateRefundNames() {
            RefundRecord r = new RefundRecord();
            r.setId(1L);
            r.setStudentId(10L);
            r.setApplicantId(2L);

            Page<RefundRecord> expectedPage = new Page<>(1, 10);
            expectedPage.setRecords(List.of(r));

            com.pzhu.eduadmin.modules.user.entity.User user = new com.pzhu.eduadmin.modules.user.entity.User();
            user.setId(2L);
            user.setRealName("李四");

            when(refundMapper.selectPage(any(Page.class), any())).thenReturn(expectedPage);
            when(studentMapper.selectNamesByIdsIncludeDeleted(any()))
                    .thenReturn(List.of(java.util.Map.of("id", 10L, "name", "张三")));
            when(userMapper.selectBatchIds(any())).thenReturn(List.of(user));

            Page<RefundRecord> result = financeService.pageRefundRecords(1, 10, null, null);

            assertThat(result.getRecords()).hasSize(1);
            assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("张三");
            assertThat(result.getRecords().get(0).getApplicantName()).isEqualTo("李四");
        }
    }
}
