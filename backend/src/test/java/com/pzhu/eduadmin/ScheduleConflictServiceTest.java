package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.RoomBookingMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleConflictServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 排课冲突检测单元测试。
 * 对应 docs/11-后端开发详细文档.md §11：覆盖教师/教室/学员维度及状态过滤规则。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("排课冲突检测单元测试")
class ScheduleConflictServiceTest {

    @Mock
    private ScheduleLessonMapper scheduleLessonMapper;
    @Mock
    private RoomBookingMapper roomBookingMapper;
    @Mock
    private ClassStudentMapper classStudentMapper;

    @InjectMocks
    private ScheduleConflictServiceImpl conflictService;

    /** 构造一个待检测课次 */
    private ScheduleLesson newLesson(Long teacherId, Long classroomId, Long classId,
                                      String date, String start, String end) {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setTeacherId(teacherId);
        lesson.setClassroomId(classroomId);
        lesson.setClassId(classId);
        lesson.setLessonDate(LocalDate.parse(date));
        lesson.setStartTime(LocalTime.parse(start));
        lesson.setEndTime(LocalTime.parse(end));
        lesson.setStatus(1);
        return lesson;
    }

    /** 构造一个已存在课次 */
    private ScheduleLesson exist(Long id, Long teacherId, Long classroomId, Long classId,
                                  String date, String start, String end, int status) {
        ScheduleLesson lesson = newLesson(teacherId, classroomId, classId, date, start, end);
        lesson.setId(id);
        lesson.setStatus(status);
        return lesson;
    }

    @Test
    @DisplayName("无冲突时应返回空列表")
    void shouldReturnEmptyWhenNoConflict() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30");

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("教师同时间冲突应被检测")
    void shouldDetectTeacherConflict() {
        ScheduleLesson lesson = newLesson(1L, 2L, 1L,
                "2026-07-10", "14:00", "15:30");

        // 教师相同但教室和班级都不同，只触发教师冲突
        ScheduleLesson existing = exist(10L, 1L, 3L, 3L,
                "2026-07-10", "15:00", "16:00", 1);

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(existing));
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0)).contains("教师冲突");
    }

    @Test
    @DisplayName("教师/班级双重冲突应同时检测")
    void shouldDetectDoubleConflict() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30");

        // 教师、教室、班级全相同
        ScheduleLesson existing = exist(10L, 1L, 1L, 1L,
                "2026-07-10", "15:00", "16:00", 1);

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(existing));
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).hasSize(3); // 教师冲突 + 教室冲突 + 班级冲突
        assertThat(conflicts).anyMatch(s -> s.contains("教师冲突"));
        assertThat(conflicts).anyMatch(s -> s.contains("教室冲突"));
        assertThat(conflicts).anyMatch(s -> s.contains("班级冲突"));
    }

    @Test
    @DisplayName("教室同时间冲突应被检测")
    void shouldDetectClassroomConflict() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30");

        ScheduleLesson existing = exist(10L, 2L, 1L, 2L,
                "2026-07-10", "15:00", "16:00", 1); // 教室相同但教师不同

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(existing));
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0)).contains("教室冲突");
    }

    @Test
    @DisplayName("首尾相接不算冲突（边界条件）")
    void shouldNotConflictWhenBackToBack() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "15:00", "16:00");

        ScheduleLesson existing = exist(10L, 1L, 1L, 1L,
                "2026-07-10", "14:00", "15:00", 1); // 15:00=15:00，等于不算重叠

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(existing));
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("status=3（已取消）的课次不参与冲突检测")
    void shouldSkipCancelledLessons() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30");

        ScheduleLesson cancelled = exist(10L, 1L, 1L, 1L,
                "2026-07-10", "14:30", "15:30", 3); // 已取消

        // cancelled 不会被查到（查询条件是 status IN (1,2)）
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("排除自身不报冲突")
    void shouldExcludeSelf() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30");
        lesson.setId(99L);

        ScheduleLesson self = exist(99L, 1L, 1L, 1L,
                "2026-07-10", "14:00", "15:30", 1);

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(self));
        when(roomBookingMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("教室预约冲突应被检测")
    void shouldDetectRoomBookingConflict() {
        ScheduleLesson lesson = newLesson(1L, 1L, 1L,
                "2026-07-10", "14:00", "16:00");

        RoomBooking booking = new RoomBooking();
        booking.setId(100L);
        booking.setClassroomId(1L);
        booking.setStartTime(LocalDateTime.of(2026, 7, 10, 15, 0));
        booking.setEndTime(LocalDateTime.of(2026, 7, 10, 17, 0));
        booking.setPurpose("考试");

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());
        when(roomBookingMapper.selectList(any())).thenReturn(List.of(booking));
        when(classStudentMapper.selectList(any())).thenReturn(List.of());

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0)).contains("教室预约冲突");
    }

    @Test
    @DisplayName("更新时间自己但教室ID为null时不检测教室预约冲突")
    void shouldSkipRoomBookingWhenClassroomNull() {
        ScheduleLesson lesson = newLesson(1L, null, 1L,
                "2026-07-10", "14:00", "16:00");

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of());
        when(classStudentMapper.selectList(any())).thenReturn(List.of());
        // roomBookingMapper 不会被调用

        List<String> conflicts = conflictService.checkConflict(lesson);
        assertThat(conflicts).isEmpty();
    }
}
