package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OrganizationMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.StatisticsSnapshotMapper;
import com.pzhu.eduadmin.modules.statistics.service.StatisticsServiceImpl;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    @Mock private UserMapper userMapper;
    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private ClassStudentMapper classStudentMapper;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Organization.class);
        TableInfoHelper.initTableInfo(assistant, OperationLog.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(assistant, Attendance.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

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
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());
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
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());

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
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());

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
        when(refundRecordMapper.selectList(any())).thenReturn(List.of());

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

    // ==================== updateOrganization ====================

    @Test
    @DisplayName("更新机构配置 - 指定ID存在时正常更新并返回")
    void updateOrganization_normalUpdate_returnsUpdatedOrg() {
        Organization input = new Organization();
        input.setId(1L);
        input.setOrgName("测试机构");
        input.setCampus("主校区");

        Organization existing = new Organization();
        existing.setId(1L);
        existing.setOrgName("旧名称");

        Organization updated = new Organization();
        updated.setId(1L);
        updated.setOrgName("测试机构");
        updated.setCampus("主校区");

        when(organizationMapper.selectById(1L)).thenReturn(existing, updated);
        when(organizationMapper.updateById(any(Organization.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Organization result = statisticsService.updateOrganization(input);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getOrgName()).isEqualTo("测试机构");
        verify(organizationMapper).updateById(any(Organization.class));
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("更新机构配置 - ID为null时回退查询已有记录")
    void updateOrganization_nullId_fallsBackToExistingRecord() {
        Organization input = new Organization();
        input.setOrgName("兜底机构");
        // id is null

        Organization existingRecord = new Organization();
        existingRecord.setId(5L);
        existingRecord.setOrgName("旧机构");

        Organization finalResult = new Organization();
        finalResult.setId(5L);
        finalResult.setOrgName("兜底机构");

        when(organizationMapper.selectOne(any())).thenReturn(existingRecord);
        when(organizationMapper.updateById(any(Organization.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);
        when(organizationMapper.selectById(5L)).thenReturn(finalResult);

        Organization result = statisticsService.updateOrganization(input);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getOrgName()).isEqualTo("兜底机构");
        // verify the input got the ID from existing record
        assertThat(input.getId()).isEqualTo(5L);
        verify(organizationMapper).selectOne(any());
        verify(organizationMapper).updateById(any(Organization.class));
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("更新机构配置 - 指定ID不存在时抛出BusinessException")
    void updateOrganization_nonExistentId_throwsException() {
        Organization input = new Organization();
        input.setId(999L);

        when(organizationMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> statisticsService.updateOrganization(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("机构配置记录不存在");
    }

    // ==================== getTeacherWorkload ====================

    @Test
    @DisplayName("教师工作量 - 正常月份返回教师课时统计")
    void getTeacherWorkload_normalMonth_returnsTeacherLessonCounts() {
        ScheduleLesson lesson1 = new ScheduleLesson();
        lesson1.setId(1L);
        lesson1.setTeacherId(10L);
        lesson1.setLessonDate(LocalDate.of(2025, 3, 5));
        lesson1.setStatus(2);

        ScheduleLesson lesson2 = new ScheduleLesson();
        lesson2.setId(2L);
        lesson2.setTeacherId(10L);
        lesson2.setLessonDate(LocalDate.of(2025, 3, 12));
        lesson2.setStatus(2);

        ScheduleLesson lesson3 = new ScheduleLesson();
        lesson3.setId(3L);
        lesson3.setTeacherId(20L);
        lesson3.setLessonDate(LocalDate.of(2025, 3, 15));
        lesson3.setStatus(2);

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(lesson1, lesson2, lesson3));

        User teacher1 = new User();
        teacher1.setId(10L);
        teacher1.setRealName("张老师");
        User teacher2 = new User();
        teacher2.setId(20L);
        teacher2.setRealName("李老师");
        when(userMapper.selectList(any())).thenReturn(List.of(teacher1, teacher2));

        List<Map<String, Object>> result = statisticsService.getTeacherWorkload("2025-03");

        assertThat(result).hasSize(2);

        // Find teacher 10 (张老师) - should have 2 lessons
        Map<String, Object> teacher10 = result.stream()
                .filter(m -> Long.valueOf(10L).equals(m.get("teacherId")))
                .findFirst().orElseThrow();
        assertThat(teacher10.get("teacherName")).isEqualTo("张老师");
        assertThat(teacher10.get("lessonCount")).isEqualTo(2L);

        // Find teacher 20 (李老师) - should have 1 lesson
        Map<String, Object> teacher20 = result.stream()
                .filter(m -> Long.valueOf(20L).equals(m.get("teacherId")))
                .findFirst().orElseThrow();
        assertThat(teacher20.get("teacherName")).isEqualTo("李老师");
        assertThat(teacher20.get("lessonCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("教师工作量 - 无课次月份返回空列表")
    void getTeacherWorkload_emptyMonth_returnsEmptyList() {
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());

        List<Map<String, Object>> result = statisticsService.getTeacherWorkload("2020-01");

        assertThat(result).isEmpty();
        verify(userMapper, never()).selectList(any());
    }

    // ==================== getStudentLossTrend ====================

    @Test
    @DisplayName("学员流失趋势 - 返回6个月数据且结构正确")
    void getStudentLossTrend_returns6MonthTrend_withCorrectStructure() {
        // All selectCount calls return 0 -> all rates are 0
        when(classStudentMapper.selectCount(any())).thenReturn(0L);

        List<Map<String, Object>> trend = statisticsService.getStudentLossTrend();

        assertThat(trend).hasSize(6);
        for (Map<String, Object> item : trend) {
            assertThat(item).containsKeys("month", "activeCount", "lossCount", "lossRate");
            assertThat(item.get("month")).isInstanceOf(String.class);
            assertThat(item.get("lossRate")).isInstanceOf(BigDecimal.class);
        }
    }

    @Test
    @DisplayName("学员流失趋势 - lossRate计算正确")
    void getStudentLossTrend_verifiesLossRateCalculation() {
        // 第一个月(5个月前): beginCount=50, newJoinCount=10, lossCount=3
        // denominator = 60, rate = 3*100/60 = 5.0
        // 后续月份全部为0
        when(classStudentMapper.selectCount(any()))
                .thenReturn(50L, 10L, 3L)   // first month: begin, newJoin, loss
                .thenReturn(0L);            // all remaining calls

        List<Map<String, Object>> trend = statisticsService.getStudentLossTrend();

        assertThat(trend).hasSize(6);

        Map<String, Object> firstMonth = trend.get(0);
        assertThat(firstMonth.get("activeCount")).isEqualTo(50L);
        assertThat(firstMonth.get("lossCount")).isEqualTo(3L);
        // lossRate = 3 * 100 / 60 = 5.0
        assertThat((BigDecimal) firstMonth.get("lossRate"))
                .isEqualByComparingTo(BigDecimal.valueOf(5.0));

        // Remaining months should all have 0 lossRate
        for (int i = 1; i < 6; i++) {
            assertThat((BigDecimal) trend.get(i).get("lossRate"))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ==================== pageOperationLogs ====================

    @Test
    @DisplayName("操作日志分页 - 返回分页结果并填充操作人姓名")
    void pageOperationLogs_returnsPaginatedResults_withOperatorNames() {
        OperationLog log1 = new OperationLog();
        log1.setId(1L);
        log1.setOperatorId(1L);
        log1.setModule("系统配置");
        log1.setOperation("更新机构配置");

        OperationLog log2 = new OperationLog();
        log2.setId(2L);
        log2.setOperatorId(2L);
        log2.setModule("学员管理");
        log2.setOperation("新增学员");

        Page<OperationLog> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(log1, log2));
        mockPage.setTotal(2);

        when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        User user1 = new User();
        user1.setId(1L);
        user1.setRealName("管理员");
        User user2 = new User();
        user2.setId(2L);
        user2.setRealName("操作员");
        when(userMapper.selectList(any())).thenReturn(List.of(user1, user2));

        Page<OperationLog> result = statisticsService.pageOperationLogs(1, 10, "配置", "createTime", "desc");

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords().get(0).getOperatorName()).isEqualTo("管理员");
        assertThat(result.getRecords().get(1).getOperatorName()).isEqualTo("操作员");
        verify(userMapper).selectList(any());
    }

    @Test
    @DisplayName("操作日志分页 - 空关键词返回全部日志")
    void pageOperationLogs_emptyKeyword_returnsAllLogs() {
        OperationLog log1 = new OperationLog();
        log1.setId(1L);
        log1.setOperatorId(1L);
        log1.setModule("系统配置");
        log1.setOperation("更新配置");

        Page<OperationLog> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(log1));
        mockPage.setTotal(1);

        when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        User user1 = new User();
        user1.setId(1L);
        user1.setRealName("管理员");
        when(userMapper.selectList(any())).thenReturn(List.of(user1));

        // null keyword should not apply any filter
        Page<OperationLog> result = statisticsService.pageOperationLogs(1, 10, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getOperatorName()).isEqualTo("管理员");
    }

    @Test
    @DisplayName("操作日志分页 - 用户realName为空时使用username")
    void pageOperationLogs_blankRealName_usesUsername() {
        OperationLog log1 = new OperationLog();
        log1.setId(1L);
        log1.setOperatorId(3L);
        log1.setModule("财务管理");
        log1.setOperation("收费");

        Page<OperationLog> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(log1));
        mockPage.setTotal(1);

        when(operationLogMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        User user = new User();
        user.setId(3L);
        user.setRealName("");
        user.setUsername("teacher_zhang");
        when(userMapper.selectList(any())).thenReturn(List.of(user));

        Page<OperationLog> result = statisticsService.pageOperationLogs(1, 10, "", null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getOperatorName()).isEqualTo("teacher_zhang");
    }
}
