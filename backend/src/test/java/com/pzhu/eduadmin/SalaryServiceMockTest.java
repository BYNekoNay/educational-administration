package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.mapper.SalaryAdjustmentMapper;
import com.pzhu.eduadmin.modules.salary.mapper.SalaryRuleMapper;
import com.pzhu.eduadmin.modules.salary.mapper.TeacherSalaryMapper;
import com.pzhu.eduadmin.modules.salary.service.SalaryServiceImpl;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("薪资核算单元测试")
class SalaryServiceMockTest {

    @Mock private SalaryRuleMapper ruleMapper;
    @Mock private TeacherSalaryMapper salaryMapper;
    @Mock private SalaryAdjustmentMapper adjustMapper;
    @Mock private ScheduleLessonMapper lessonMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;

    @InjectMocks
    private SalaryServiceImpl salaryService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SalaryRule.class);
        TableInfoHelper.initTableInfo(assistant, TeacherSalary.class);
        TableInfoHelper.initTableInfo(assistant, SalaryAdjustment.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(assistant, Attendance.class);
        TableInfoHelper.initTableInfo(assistant, ClassGroup.class);
    }

    @BeforeEach
    void setUp() { CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN")); }

    @AfterEach
    void tearDown() { CurrentUserHolder.clear(); }

    // ==================== createSalaryRule ====================

    @Test
    @DisplayName("创建薪资规则 - 正常成功")
    void createSalaryRule_shouldSucceed() {
        SalaryRule rule = new SalaryRule();
        rule.setTeacherId(1L);
        rule.setCourseId(100L);
        rule.setLessonUnitPrice(new BigDecimal("150"));

        when(ruleMapper.selectCount(any())).thenReturn(0L);
        when(ruleMapper.insert(any(SalaryRule.class))).thenReturn(1);

        SalaryRule result = salaryService.createSalaryRule(rule);

        assertThat(result).isSameAs(rule);
        assertThat(result.getSubstituteRate()).isEqualTo(BigDecimal.ONE); // default set
        verify(ruleMapper).insert(any(SalaryRule.class));
    }

    @Test
    @DisplayName("创建薪资规则 - 教师+课程重复则拒绝")
    void createSalaryRule_shouldRejectDuplicate() {
        SalaryRule rule = new SalaryRule();
        rule.setTeacherId(1L);
        rule.setCourseId(100L);
        rule.setLessonUnitPrice(new BigDecimal("150"));

        when(ruleMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> salaryService.createSalaryRule(rule))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可重复创建");

        verify(ruleMapper, never()).insert(any(SalaryRule.class));
    }

    // ==================== updateSalaryRule ====================

    @Test
    @DisplayName("更新薪资规则 - 正常成功")
    void updateSalaryRule_shouldSucceed() {
        SalaryRule existing = new SalaryRule();
        existing.setId(1L);
        existing.setTeacherId(1L);
        existing.setCourseId(100L);
        existing.setLessonUnitPrice(new BigDecimal("100"));

        SalaryRule toUpdate = new SalaryRule();
        toUpdate.setId(1L);
        toUpdate.setLessonUnitPrice(new BigDecimal("200"));

        when(ruleMapper.selectById(1L)).thenReturn(existing).thenReturn(toUpdate);
        when(ruleMapper.updateById(any(SalaryRule.class))).thenReturn(1);

        SalaryRule result = salaryService.updateSalaryRule(toUpdate);

        assertThat(result).isNotNull();
        verify(ruleMapper).updateById(any(SalaryRule.class));
    }

    @Test
    @DisplayName("更新薪资规则 - 规则不存在则404")
    void updateSalaryRule_shouldThrowWhenNotFound() {
        SalaryRule toUpdate = new SalaryRule();
        toUpdate.setId(999L);
        toUpdate.setLessonUnitPrice(new BigDecimal("200"));

        when(ruleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> salaryService.updateSalaryRule(toUpdate))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("薪资规则不存在");

        verify(ruleMapper, never()).updateById(any(SalaryRule.class));
    }

    // ==================== deleteSalaryRule ====================

    @Test
    @DisplayName("删除薪资规则 - 正常成功")
    void deleteSalaryRule_shouldSucceed() {
        when(ruleMapper.deleteById(1L)).thenReturn(1);

        salaryService.deleteSalaryRule(1L);

        verify(ruleMapper).deleteById(1L);
    }

    // ==================== calculateSalary happy path ====================

    @Test
    @DisplayName("薪资核算 - 主讲+代课正常计算")
    void calculateSalary_happyPath() {
        // --- main lessons (sourceLessonId=null, status=2) ---
        ScheduleLesson lesson1 = new ScheduleLesson();
        lesson1.setId(101L);
        lesson1.setClassId(10L);
        lesson1.setTeacherId(1L);
        lesson1.setStatus(2);
        lesson1.setLessonDate(LocalDate.of(2026, 7, 5));

        ScheduleLesson lesson2 = new ScheduleLesson();
        lesson2.setId(102L);
        lesson2.setClassId(10L);
        lesson2.setTeacherId(1L);
        lesson2.setStatus(2);
        lesson2.setLessonDate(LocalDate.of(2026, 7, 12));

        // --- substitute lesson (sourceLessonId != null, status=2) ---
        ScheduleLesson subLesson = new ScheduleLesson();
        subLesson.setId(201L);
        subLesson.setClassId(10L);
        subLesson.setTeacherId(1L);
        subLesson.setStatus(2);
        subLesson.setLessonDate(LocalDate.of(2026, 7, 8));
        subLesson.setSourceLessonId(999L);

        // --- attendance records (status=1 means present) ---
        Attendance att1 = new Attendance();
        att1.setLessonId(101L);
        att1.setStatus(1);

        Attendance att2 = new Attendance();
        att2.setLessonId(102L);
        att2.setStatus(1);

        Attendance att3 = new Attendance();
        att3.setLessonId(201L);
        att3.setStatus(1);

        // --- salary rule: unitPrice=100, substituteRate=1.5 ---
        SalaryRule rule = new SalaryRule();
        rule.setId(1L);
        rule.setTeacherId(1L);
        rule.setCourseId(100L);
        rule.setLessonUnitPrice(new BigDecimal("100"));
        rule.setSubstituteRate(new BigDecimal("1.5"));

        // --- classGroup: classId=10 -> courseId=100 ---
        ClassGroup cg = new ClassGroup();
        cg.setId(10L);
        cg.setCourseId(100L);

        // --- stubs ---
        // first call returns main lessons, second call returns substitute lessons
        when(lessonMapper.selectList(any()))
                .thenReturn(List.of(lesson1, lesson2))
                .thenReturn(List.of(subLesson));
        when(attendanceMapper.selectList(any())).thenReturn(List.of(att1, att2, att3));
        when(ruleMapper.selectList(any())).thenReturn(List.of(rule));
        when(classGroupMapper.selectList(any())).thenReturn(List.of(cg));
        when(salaryMapper.selectOne(any())).thenReturn(null);
        when(salaryMapper.insert(any(TeacherSalary.class))).thenReturn(1);

        TeacherSalary result = salaryService.calculateSalary("2026-07", 1L, BigDecimal.ZERO);

        // --- assertions ---
        assertThat(result).isNotNull();
        assertThat(result.getLessonCount()).isEqualByComparingTo(new BigDecimal("2"));
        assertThat(result.getSubstituteCount()).isEqualByComparingTo(new BigDecimal("1"));
        assertThat(result.getBaseAmount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(result.getBonusAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        // totalAmount = baseAmount(200) + substituteAmount(150) + bonus(0) = 350
        assertThat(result.getTotalAmount()).isEqualByComparingTo(new BigDecimal("350.00"));
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getSalaryMonth()).isEqualTo("2026-07");

        verify(salaryMapper).insert(any(TeacherSalary.class));
        verify(salaryMapper, never()).updateById(any(TeacherSalary.class));
    }

    // ==================== createAdjustment ====================

    @Test
    @DisplayName("创建薪资调整 - 已确认薪资可调整，且totalAmount被更新")
    void createAdjustment_shouldSucceedOnConfirmedSalary() {
        TeacherSalary confirmed = new TeacherSalary();
        confirmed.setId(1L);
        confirmed.setStatus(2); // 已确认
        confirmed.setBaseAmount(new BigDecimal("3000"));
        confirmed.setBonusAmount(new BigDecimal("200"));

        when(salaryMapper.selectById(1L)).thenReturn(confirmed);
        when(adjustMapper.insert(any(SalaryAdjustment.class))).thenReturn(1);
        // H12 fix: 现在使用原子 SQL 递增 totalAmount
        when(salaryMapper.update(any(), any())).thenReturn(1);

        SalaryAdjustment result = salaryService.createAdjustment(
                1L, new BigDecimal("500"), "绩效奖金", 1L);

        assertThat(result).isNotNull();
        assertThat(result.getTeacherSalaryId()).isEqualTo(1L);
        assertThat(result.getAdjustAmount()).isEqualByComparingTo(new BigDecimal("500"));
        assertThat(result.getReason()).isEqualTo("绩效奖金");
        assertThat(result.getOperatorId()).isEqualTo(1L);

        // H12 fix: totalAmount 通过原子 SQL 递增，验证 update(null, wrapper) 被调用
        verify(adjustMapper).insert(any(SalaryAdjustment.class));
        verify(salaryMapper).update(any(), any());
    }

    @Test
    @DisplayName("创建薪资调整 - 非已确认薪资则拒绝")
    void createAdjustment_shouldRejectOnNonConfirmedSalary() {
        TeacherSalary pending = new TeacherSalary();
        pending.setId(2L);
        pending.setStatus(1); // 待确认，非已确认

        when(salaryMapper.selectById(2L)).thenReturn(pending);

        assertThatThrownBy(() -> salaryService.createAdjustment(
                2L, new BigDecimal("500"), "绩效奖金", 1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");

        verify(adjustMapper, never()).insert(any(SalaryAdjustment.class));
    }

    // ==================== existing tests ====================

    @Test
    @DisplayName("无薪资规则时核算应抛异常")
    void shouldThrowWhenNoRule() {
        when(lessonMapper.selectList(any())).thenReturn(java.util.List.of());
        when(ruleMapper.selectList(any())).thenReturn(java.util.List.of());

        assertThatThrownBy(() ->
                salaryService.calculateSalary("2026-07", 1L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("薪资规则");
    }

    @Test
    @DisplayName("已确认薪资不可覆盖")
    void shouldRejectOverwriteConfirmed() {
        TeacherSalary existing = new TeacherSalary();
        existing.setId(1L); existing.setStatus(2);

        SalaryRule rule = new SalaryRule();
        rule.setLessonUnitPrice(new java.math.BigDecimal("100"));
        rule.setSubstituteRate(new java.math.BigDecimal("0.8"));
        rule.setCourseId(1L);

        when(lessonMapper.selectList(any())).thenReturn(java.util.List.of());
        when(ruleMapper.selectList(any())).thenReturn(java.util.List.of(rule));
        when(salaryMapper.selectOne(any())).thenReturn(existing);

        assertThatThrownBy(() ->
                salaryService.calculateSalary("2026-07", 1L, BigDecimal.ZERO))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已确认");
    }

    @Test
    @DisplayName("确认薪资状态置为 2")
    void shouldConfirmSalary() {
        TeacherSalary salary = new TeacherSalary();
        salary.setId(1L); salary.setTeacherId(1L); salary.setStatus(1);

        when(salaryMapper.selectById(1L)).thenReturn(salary);
        // CAS 更新：update(null, wrapper)
        doReturn(1).when(salaryMapper).update(any(), any(LambdaUpdateWrapper.class));
        lenient().when(nameResolver.getUserDisplayName(anyLong())).thenReturn("教师A");

        TeacherSalary result = salaryService.confirmSalary(1L);
        assertThat(result.getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("作废薪资状态置为 4")
    void shouldVoidSalary() {
        TeacherSalary salary = new TeacherSalary();
        salary.setId(1L); salary.setTeacherId(1L); salary.setStatus(2);

        when(salaryMapper.selectById(1L)).thenReturn(salary);
        // CAS 更新：update(null, wrapper)
        doReturn(1).when(salaryMapper).update(any(), any(LambdaUpdateWrapper.class));
        lenient().when(nameResolver.getUserDisplayName(anyLong())).thenReturn("教师A");

        TeacherSalary result = salaryService.voidSalary(1L);
        assertThat(result.getStatus()).isEqualTo(4);
    }
}
