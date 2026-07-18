package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentServiceImpl;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("报名管理单元测试")
class EnrollmentServiceMockTest {

    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private StudentMapper studentMapper;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private RefundRecordMapper refundRecordMapper;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
        TableInfoHelper.initTableInfo(assistant, RefundRecord.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ========== create() ==========

    @Test
    @DisplayName("create - 正常创建报名成功")
    void create_success() {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);
        // P1-1: create 需校验学员/课程存在
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(new com.pzhu.eduadmin.modules.course.entity.Course());

        Enrollment result = enrollmentService.create(enrollment);

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo(10L);
        assertThat(result.getCourseId()).isEqualTo(20L);
        verify(enrollmentMapper).insert(enrollment);
    }

    @Test
    @DisplayName("create - 学员已报名同课程（status=2或3）应拒绝")
    void create_duplicateEnrollment_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);

        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // P1-1: create 需校验学员/课程存在
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(new com.pzhu.eduadmin.modules.course.entity.Course());

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该学员已有此课程的报名记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("create - studentId 为空应抛异常")
    void create_nullStudentId_shouldThrow() {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(20L);

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    // ========== audit() ==========

    @Test
    @DisplayName("audit - 正常审核通过（status=2），应设置 holdExpireTime")
    void audit_approve_shouldSetHoldExpireTime() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setStatus(1);
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);

        when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
        // CAS 更新：update(null, wrapper)
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");
        when(nameResolver.getCourseName(anyLong())).thenReturn("测试课程");

        Enrollment result = enrollmentService.audit(1L, 2, 1L, "审核通过");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getAuditorId()).isEqualTo(1L);
        assertThat(result.getAuditRemark()).isEqualTo("审核通过");
        assertThat(result.getHoldExpireTime()).isNotNull();
        verify(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("audit - 审核驳回（status=4）")
    void audit_reject_shouldSetStatus4() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(2L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectById(2L)).thenReturn(enrollment);
        // CAS 更新：update(null, wrapper)
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        Enrollment result = enrollmentService.audit(2L, 4, 1L, "资料不全");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(4);
        assertThat(result.getAuditorId()).isEqualTo(1L);
        assertThat(result.getAuditRemark()).isEqualTo("资料不全");
        assertThat(result.getHoldExpireTime()).isNull();
        verify(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("audit - 非待审核状态的报名不可审核")
    void audit_notPending_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(3L);
        enrollment.setStatus(2); // 已通过，非待审核

        when(enrollmentMapper.selectById(3L)).thenReturn(enrollment);

        assertThatThrownBy(() -> enrollmentService.audit(3L, 2, 1L, "再次审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非待审核状态")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("audit - 无效审核状态（非2非4）应拒绝")
    void audit_invalidStatus_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(4L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectById(4L)).thenReturn(enrollment);

        assertThatThrownBy(() -> enrollmentService.audit(4L, 3, 1L, "无效状态"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的审核状态")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(enrollmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ========== delete() ==========

    @Test
    @DisplayName("delete - 正常删除成功")
    void delete_success() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(5L);
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(30L);

        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectById(5L)).thenReturn(enrollment);
        when(enrollmentMapper.deleteById(5L)).thenReturn(1);
        when(classStudentMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        lenient().when(nameResolver.getStudentName(anyLong())).thenReturn("学员A");
        lenient().when(nameResolver.getCourseName(anyLong())).thenReturn("课程A");

        boolean result = enrollmentService.delete(5L);

        assertThat(result).isTrue();
        verify(enrollmentMapper).deleteById(5L);
        verify(classStudentMapper).delete(any(LambdaQueryWrapper.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("delete - 有缴费记录时拒绝删除")
    void delete_hasPaymentRecords_shouldReject() {
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.delete(6L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缴费记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete - 有退费记录时拒绝删除")
    void delete_hasRefundRecords_shouldReject() {
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.delete(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退费记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    // ========== expirePendingEnrollments() ==========

    @Test
    @DisplayName("expirePendingEnrollments - 定时任务正常执行，过期报名标记为已失效")
    void expirePendingEnrollments_shouldUpdateExpiredRecords() {
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(3);

        enrollmentService.expirePendingEnrollments();

        verify(enrollmentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("expirePendingEnrollments - 无过期记录时不打印日志")
    void expirePendingEnrollments_noExpiredRecords() {
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        enrollmentService.expirePendingEnrollments();

        verify(enrollmentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }
}
