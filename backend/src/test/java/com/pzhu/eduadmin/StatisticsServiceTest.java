package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OrganizationMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.StatisticsSnapshotMapper;
import com.pzhu.eduadmin.modules.statistics.service.StatisticsServiceImpl;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 统计看板关键指标计算单元测试。
 * 对应 docs/11-后端开发详细文档.md §11：覆盖在册学员、到课率、营收等关键统计。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("统计服务单元测试")
class StatisticsServiceTest {

    @Mock private StatisticsSnapshotMapper snapshotMapper;
    @Mock private OrganizationMapper organizationMapper;
    @Mock private OperationLogMapper operationLogMapper;
    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private ClassStudentMapper classStudentMapper;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("Dashboard 应返回 cards 和 charts 数据")
    void shouldReturnDashboardWithCardsAndCharts() {
        when(classStudentMapper.selectCount(any())).thenReturn(25L);
        // 本月已完成课次
        when(scheduleLessonMapper.selectCount(any())).thenReturn(80L);
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());
        when(attendanceMapper.selectCount(any())).thenReturn(200L, 160L); // total, attended

        // charts: lessonTrend / revenueTrend (scheduleLessonMapper.selectCount 会被多次调用)
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> dashboard = statisticsService.getDashboard();
        assertThat(dashboard).containsKeys("cards", "charts");

        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        assertThat(cards).containsKeys("activeStudents", "monthlyLessons", "attendanceRate");
    }

    @Test
    @DisplayName("到课率为100%时正确计算")
    void shouldCalcPerfectAttendanceRate() {
        // 全部到课
        when(classStudentMapper.selectCount(any())).thenReturn(10L);
        when(scheduleLessonMapper.selectCount(any())).thenReturn(30L);
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());

        // totalAttendance = 100, attendedCount = 100
        when(attendanceMapper.selectCount(any())).thenReturn(100L, 100L);
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> dashboard = statisticsService.getDashboard();
        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        assertThat(cards.get("attendanceRate")).isEqualTo(BigDecimal.valueOf(100.0));
    }

    @Test
    @DisplayName("到课率为0%时正确计算")
    void shouldCalcZeroAttendanceRate() {
        when(classStudentMapper.selectCount(any())).thenReturn(5L);
        when(scheduleLessonMapper.selectCount(any())).thenReturn(0L);
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of());

        // totalAttendance = 0
        when(attendanceMapper.selectCount(any())).thenReturn(0L, 0L);
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());

        Map<String, Object> dashboard = statisticsService.getDashboard();
        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        assertThat(cards.get("attendanceRate")).isEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("营收统计应正确处理多条记录")
    void shouldSumRevenueCorrectly() {
        when(classStudentMapper.selectCount(any())).thenReturn(10L);
        when(scheduleLessonMapper.selectCount(any())).thenReturn(30L);
        when(attendanceMapper.selectCount(any())).thenReturn(50L, 40L);
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());

        PaymentRecord pr1 = new PaymentRecord();
        pr1.setAmount(BigDecimal.valueOf(1000));
        PaymentRecord pr2 = new PaymentRecord();
        pr2.setAmount(BigDecimal.valueOf(2000));
        when(paymentRecordMapper.selectList(any())).thenReturn(List.of(pr1, pr2));

        Map<String, Object> dashboard = statisticsService.getDashboard();
        @SuppressWarnings("unchecked")
        Map<String, Object> cards = (Map<String, Object>) dashboard.get("cards");
        assertThat(((BigDecimal) cards.get("monthlyRevenue")).compareTo(BigDecimal.valueOf(3000.00))).isZero();
    }

    @Test
    @DisplayName("机构信息查询应返回组织配置")
    void shouldGetOrganization() {
        Organization org = new Organization();
        org.setId(1L);
        when(organizationMapper.selectOne(any())).thenReturn(org);

        Organization result = statisticsService.getOrganization();
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
    }
}
