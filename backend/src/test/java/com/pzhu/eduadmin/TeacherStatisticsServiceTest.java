package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.schedule.statistics.TeacherStatisticsServiceImpl;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TeacherStatisticsServiceTest {

    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @InjectMocks private TeacherStatisticsServiceImpl statisticsService;

    private static final Long TEACHER_ID = 5L;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(assistant, Attendance.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(TEACHER_ID, "teacher", "TEACHER"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("概览统计 — 有数据应返回正确统计")
    void overview_withData_returnsStats() {
        // 当月课程
        ScheduleLesson sl1 = lesson(1L, LocalDate.now().withDayOfMonth(1), LocalTime.of(9, 0), LocalTime.of(10, 30), 2);
        ScheduleLesson sl2 = lesson(2L, LocalDate.now().withDayOfMonth(2), LocalTime.of(14, 0), LocalTime.of(15, 0), 1);
        // 上月课程
        ScheduleLesson sl3 = lesson(3L, LocalDate.now().minusMonths(1).withDayOfMonth(3), LocalTime.of(10, 0), LocalTime.of(11, 0), 2);

        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L, 2L, 1L, 2L);
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sl1, sl2, sl3))  // getMonthly buildMonthStat 调用两次会复用
                .thenReturn(List.of(sl1, sl2, sl3))  // 第二个月
                .thenReturn(List.of(new ScheduleLesson() {{ setId(1L); }}, new ScheduleLesson() {{ setId(2L); }}, new ScheduleLesson() {{ setId(3L); }})); // lessonIds for attendance calc

        Attendance a1 = new Attendance(); a1.setStudentId(10L); a1.setStatus(1);
        Attendance a2 = new Attendance(); a2.setStudentId(11L); a2.setStatus(2);
        Attendance a3 = new Attendance(); a3.setStudentId(10L); a3.setStatus(3);
        when(attendanceMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L, 2L);
        when(attendanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(a1, a2, a3));

        Map<String, Object> result = statisticsService.getOverview(TEACHER_ID);

        assertThat(result).containsKeys("thisMonth", "lastMonth", "completionRate", "attendanceRate", "substituteCount");
        assertThat(result.get("completionRate")).isEqualTo(0.67);
        assertThat(result.get("attendanceRate")).isEqualTo(0.67);
        assertThat(result.get("substituteCount")).isEqualTo(1L);
    }

    @Test
    @DisplayName("概览统计 — 无数据应返回零值")
    void overview_emptyData_returnsZeros() {
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        Map<String, Object> result = statisticsService.getOverview(TEACHER_ID);

        assertThat(result.get("completionRate")).isEqualTo(0.0);
        assertThat(result.get("attendanceRate")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("月度统计 — 按月分组正确")
    void monthly_groupsByMonth() {
        ScheduleLesson jan = lesson(1L, LocalDate.of(2026, 1, 10), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        ScheduleLesson feb = lesson(2L, LocalDate.of(2026, 2, 5), LocalTime.of(14, 0), LocalTime.of(15, 30), 2);

        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(jan, feb));
        when(attendanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = statisticsService.getMonthly(TEACHER_ID, null);

        assertThat(result).hasSize(2);
        assertThat(result.get(0).get("month")).isEqualTo("2026-01");
        assertThat(result.get(0).get("lessonCount")).isEqualTo(1L);
        assertThat(result.get(1).get("month")).isEqualTo("2026-02");
    }

    @Test
    @DisplayName("月度统计 — 指定年份筛选")
    void monthly_yearFilter() {
        ScheduleLesson s2025 = lesson(1L, LocalDate.of(2025, 12, 1), LocalTime.of(9, 0), LocalTime.of(10, 0), 1);
        ScheduleLesson s2026 = lesson(2L, LocalDate.of(2026, 1, 5), LocalTime.of(14, 0), LocalTime.of(15, 0), 1);

        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s2025, s2026));
        when(attendanceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = statisticsService.getMonthly(TEACHER_ID, 2026);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).get("month")).isEqualTo("2026-01");
    }

    @Test
    @DisplayName("月度统计 — 无数据返回空列表")
    void monthly_emptyData_returnsEmptyList() {
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        List<Map<String, Object>> result = statisticsService.getMonthly(TEACHER_ID, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("完成率 — 全是已完成应为1.0")
    void completionRate_allCompleted() {
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(5L)  // total
                .thenReturn(5L); // completed

        Map<String, Object> result = statisticsService.getOverview(TEACHER_ID);
        assertThat(result.get("completionRate")).isEqualTo(1.0);
    }

    private ScheduleLesson lesson(Long id, LocalDate date, LocalTime start, LocalTime end, int status) {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(id);
        sl.setTeacherId(TEACHER_ID);
        sl.setLessonDate(date);
        sl.setStartTime(start);
        sl.setEndTime(end);
        sl.setStatus(status);
        return sl;
    }
}
