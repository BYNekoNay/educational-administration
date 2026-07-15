package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.salary.mapper.*;
import com.pzhu.eduadmin.modules.salary.service.SalaryServiceImpl;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
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
@DisplayName("薪资核算单元测试")
class SalaryServiceMockTest {

    @Mock private SalaryRuleMapper ruleMapper;
    @Mock private TeacherSalaryMapper salaryMapper;
    @Mock private SalaryAdjustmentMapper adjustMapper;
    @Mock private ScheduleLessonMapper lessonMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper operationLogMapper;

    @InjectMocks
    private SalaryServiceImpl salaryService;

    @BeforeEach
    void setUp() { CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN")); }

    @AfterEach
    void tearDown() { CurrentUserHolder.clear(); }

    @Test
    @DisplayName("无薪资规则时核算应抛异常")
    void shouldThrowWhenNoRule() {
        when(lessonMapper.selectList(any())).thenReturn(java.util.List.of());
        when(attendanceMapper.selectList(any())).thenReturn(java.util.List.of());
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
        existing.setId(1L); existing.setStatus(2); // 已确认

        SalaryRule rule = new SalaryRule();
        rule.setLessonUnitPrice(new java.math.BigDecimal("100"));
        rule.setSubstituteRate(new java.math.BigDecimal("0.8"));

        when(lessonMapper.selectList(any())).thenReturn(java.util.List.of());
        when(attendanceMapper.selectList(any())).thenReturn(java.util.List.of());
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
        salary.setId(1L); salary.setStatus(1);

        when(salaryMapper.selectById(1L)).thenReturn(salary);
        doReturn(1).when(salaryMapper).updateById(any(TeacherSalary.class));

        TeacherSalary result = salaryService.confirmSalary(1L);
        assertThat(result.getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("作废薪资状态置为 4")
    void shouldVoidSalary() {
        TeacherSalary salary = new TeacherSalary();
        salary.setId(1L); salary.setStatus(2);

        when(salaryMapper.selectById(1L)).thenReturn(salary);
        doReturn(1).when(salaryMapper).updateById(any(TeacherSalary.class));

        TeacherSalary result = salaryService.voidSalary(1L);
        assertThat(result.getStatus()).isEqualTo(4);
    }
}
