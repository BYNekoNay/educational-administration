package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.finance.entity.*;
import com.pzhu.eduadmin.modules.finance.mapper.*;
import com.pzhu.eduadmin.modules.finance.service.FinanceServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("退费审计单元测试")
class FinanceServiceMockTest {

    @Mock private PaymentRecordMapper paymentMapper;
    @Mock private RefundRecordMapper refundMapper;
    @Mock private LessonAccountMapper accountMapper;
    @Mock private LessonFlowMapper flowMapper;
    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper classGroupMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper operationLogMapper;

    @InjectMocks
    private FinanceServiceImpl financeService;

    @BeforeEach
    void setUp() { CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN")); }

    @AfterEach
    void tearDown() { CurrentUserHolder.clear(); }

    @Test
    @DisplayName("退费金额超上限应抛异常")
    void shouldRejectExcessRefund() {
        RefundRecord record = new RefundRecord();
        record.setId(1L); record.setStatus(1);
        record.setEnrollmentId(1L); record.setStudentId(1L);
        record.setApplicantId(1L); // admin 申请

        when(refundMapper.selectById(1L)).thenReturn(record);
        when(paymentMapper.sumByEnrollmentId(1L)).thenReturn(new BigDecimal("2400"));
        when(refundMapper.sumApprovedByEnrollmentId(1L)).thenReturn(BigDecimal.ZERO);
        when(enrollmentMapper.selectCourseIdById(1L)).thenReturn(1L);

        assertThatThrownBy(() ->
                financeService.auditRefund(1L, 2, 4L, new BigDecimal("99999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超出");
    }

    @Test
    @DisplayName("审核人与申请人同人应拒绝")
    void shouldRejectSamePersonAudit() {
        RefundRecord record = new RefundRecord();
        record.setId(2L); record.setStatus(1);
        record.setApplicantId(4L); // finance 申请

        when(refundMapper.selectById(2L)).thenReturn(record);

        assertThatThrownBy(() ->
                financeService.auditRefund(2L, 2, 4L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一人");
    }

    @Test
    @DisplayName("审核拒绝仅更新状态")
    void shouldRejectRefund() {
        RefundRecord record = new RefundRecord();
        record.setId(3L); record.setStatus(1);
        record.setApplicantId(1L);

        when(refundMapper.selectById(3L)).thenReturn(record);
        doReturn(1).when(refundMapper).updateById(any(RefundRecord.class));

        RefundRecord result = financeService.auditRefund(3L, 3, 4L, BigDecimal.ZERO);
        assertThat(result.getStatus()).isEqualTo(3);
        assertThat(result.getAuditorId()).isEqualTo(4L);
    }
}
