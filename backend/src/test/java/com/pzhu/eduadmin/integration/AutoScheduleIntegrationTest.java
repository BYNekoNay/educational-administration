package com.pzhu.eduadmin.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.schedule.dto.AutoScheduleRequest;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ClassroomMapper;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成测试：智能排课（真实 DB + schedule_lock 行锁 + 教师单日上限约束）。
 * 验证"星期集合逐日生成""容量筛选""教师单日课次上限跳过候选日"等规则的真实落库行为。
 */
@DisplayName("集成测试：智能排课（真实 DB）")
class AutoScheduleIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private ScheduleService scheduleService;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private ClassroomMapper classroomMapper;

    private Long givenTeacher() {
        User teacher = new User();
        teacher.setUsername("it-teacher-" + System.nanoTime());
        teacher.setPassword("not-used");
        teacher.setRealName("集成教师");
        teacher.setRoleCode("TEACHER");
        teacher.setStatus(1);
        userMapper.insert(teacher);
        return teacher.getId();
    }

    private Long givenClassroom(String name, int capacity) {
        Classroom room = new Classroom();
        room.setName(name);
        room.setCapacity(capacity);
        room.setStatus(1);
        classroomMapper.insert(room);
        return room.getId();
    }

    private AutoScheduleRequest baseRequest(Long classId, Long teacherId, LocalDate firstDay) {
        AutoScheduleRequest request = new AutoScheduleRequest();
        request.setClassId(classId);
        request.setTeacherId(teacherId);
        request.setStartDate(firstDay);
        request.setEndDate(firstDay.plusWeeks(3));
        request.setStartTime(LocalTime.of(10, 0));
        request.setEndTime(LocalTime.of(11, 0));
        request.setWeekdays(List.of(1)); // 每周一
        return request;
    }

    @Test
    @DisplayName("按星期集合逐日生成课次：3 个周一各 1 节，全部落库为待上课")
    void autoScheduleGeneratesLessonsOnMatchingWeekdays() {
        Course course = givenCourse("集成课程-排课", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 8);
        Long teacherId = givenTeacher();
        givenClassroom("集成教室-A", 10);

        LocalDate firstMonday = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);
        AutoScheduleRequest request = baseRequest(classGroup.getId(), teacherId, firstMonday);
        request.setLessonCount(3);

        List<ScheduleLesson> generated = scheduleService.autoSchedule(request);

        assertThat(generated).hasSize(3);
        assertThat(generated).extracting(ScheduleLesson::getLessonDate)
                .containsExactly(firstMonday, firstMonday.plusWeeks(1), firstMonday.plusWeeks(2));
        assertThat(generated).allSatisfy(lesson -> assertThat(lesson.getStatus()).isEqualTo(1));

        // 真实落库：仅本班级 3 条课次
        assertThat(scheduleLessonMapper.selectCount(new LambdaQueryWrapper<ScheduleLesson>()
                .eq(ScheduleLesson::getClassId, classGroup.getId()))).isEqualTo(3);
    }

    @Test
    @DisplayName("教师单日课次上限：已达上限的候选日被跳过，课次顺延到下一个可用日期")
    void autoScheduleRespectsTeacherDailyCap() {
        Course course = givenCourse("集成课程-限排", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 8);
        Long teacherId = givenTeacher();
        Long roomId = givenClassroom("集成教室-B", 10);

        LocalDate firstMonday = LocalDate.now().plusWeeks(1).with(DayOfWeek.MONDAY);
        // 教师在该周一已有一节课（其他班级），达到单日上限 1
        ScheduleLesson existing = new ScheduleLesson();
        existing.setClassId(999L);
        existing.setTeacherId(teacherId);
        existing.setClassroomId(roomId);
        existing.setLessonDate(firstMonday);
        existing.setStartTime(LocalTime.of(9, 0));
        existing.setEndTime(LocalTime.of(10, 0));
        existing.setStatus(1);
        scheduleLessonMapper.insert(existing);

        AutoScheduleRequest request = baseRequest(classGroup.getId(), teacherId, firstMonday);
        request.setLessonCount(1);
        request.setMaxLessonsPerDay(1);

        List<ScheduleLesson> generated = scheduleService.autoSchedule(request);

        assertThat(generated).hasSize(1);
        assertThat(generated.get(0).getLessonDate()).isEqualTo(firstMonday.plusWeeks(1));
    }
}