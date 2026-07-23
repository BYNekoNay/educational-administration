package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OrganizationMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.StatisticsSnapshotMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.statistics.service.StatisticsServiceImpl;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("统计服务 Mock 单元测试")
class StatisticsServiceTest {

    @Mock private StatisticsSnapshotMapper snapshotMapper;
    @Mock private OrganizationMapper organizationMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private UserMapper userMapper;
    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private CourseMapper courseMapper;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Course.class);
        TableInfoHelper.initTableInfo(asst, ClassGroup.class);
        TableInfoHelper.initTableInfo(asst, Organization.class);
        TableInfoHelper.initTableInfo(asst, Enrollment.class);
    }

    @Test
    @DisplayName("课程盈利 — 有数据应返回正确的净收入")
    void getCourseProfit_withData() {
        Course c1 = new Course(); c1.setId(1L); c1.setName("钢琴");
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1));

        PaymentRecord p = new PaymentRecord(); p.setCourseId(1L); p.setAmount(new BigDecimal("5000"));
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));
        when(refundRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = statisticsService.getCourseProfit();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("netProfit")).isEqualTo(new BigDecimal("5000.00"));
    }

    @Test
    @DisplayName("课程盈利 — 包含退费应扣除")
    void getCourseProfit_withRefund() {
        Course c1 = new Course(); c1.setId(1L); c1.setName("钢琴");
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1));

        PaymentRecord p = new PaymentRecord(); p.setCourseId(1L); p.setAmount(new BigDecimal("5000"));
        RefundRecord r = new RefundRecord(); r.setEnrollmentId(10L); r.setAmount(new BigDecimal("1000"));
        Enrollment en = new Enrollment(); en.setId(10L); en.setCourseId(1L);

        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));
        when(refundRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));
        when(enrollmentMapper.selectBatchIdsIncludeDeleted(anySet())).thenReturn(List.of(en));

        List<Map<String, Object>> result = statisticsService.getCourseProfit();
        assertThat(result.get(0).get("netProfit")).isEqualTo(new BigDecimal("4000.00"));
    }

    @Test
    @DisplayName("课程盈利 — 无数据返回空列表")
    void getCourseProfit_empty() {
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        assertThat(statisticsService.getCourseProfit()).isEmpty();
    }

    @Test
    @DisplayName("收费率 — 全部已缴费应返回 1.0")
    void getPaymentRate_withData() {
        Course c1 = new Course(); c1.setId(1L); c1.setName("钢琴");
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1));

        // 新逻辑：按报名数比率计算，缴费记录通过 enrollmentId 关联
        Enrollment e = new Enrollment(); e.setId(100L); e.setCourseId(1L); e.setStatus(2);
        when(enrollmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(e));

        PaymentRecord p = new PaymentRecord(); p.setEnrollmentId(100L);
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));

        List<Map<String, Object>> result = statisticsService.getPaymentRate();
        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("rate")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("收费率 — 未收满")
    void getPaymentRate_notFull() {
        Course c1 = new Course(); c1.setId(1L); c1.setName("钢琴");
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(c1));

        // 5 个报名，其中 3 个已缴费 → rate = 3/5 = 0.6
        List<Enrollment> enrollments = new ArrayList<>();
        for (long i = 100; i < 105; i++) {
            Enrollment e = new Enrollment(); e.setId(i); e.setCourseId(1L); e.setStatus(2);
            enrollments.add(e);
        }
        when(enrollmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(enrollments);

        List<PaymentRecord> payments = new ArrayList<>();
        for (long i = 100; i < 103; i++) {
            PaymentRecord p = new PaymentRecord(); p.setEnrollmentId(i);
            payments.add(p);
        }
        when(paymentRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(payments);

        List<Map<String, Object>> result = statisticsService.getPaymentRate();
        assertThat(result.get(0).get("rate")).isEqualTo(0.6);
    }
}
