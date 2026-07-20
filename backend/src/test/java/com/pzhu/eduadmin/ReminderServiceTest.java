package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.notification.service.ReminderService;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("自动提醒服务")
class ReminderServiceTest {

    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ParentStudentMapper parentStudentMapper;
    @Mock private LessonAccountMapper lessonAccountMapper;
    @Mock private NotificationService notificationService;

    @InjectMocks private ReminderService reminderService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ParentStudent.class);
        TableInfoHelper.initTableInfo(assistant, LessonAccount.class);
        TableInfoHelper.initTableInfo(assistant, Notification.class);
    }

    @Test
    @DisplayName("课程开始前向教师和家长发送提醒")
    void sendLessonReminders() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(10L);
        lesson.setClassId(20L);
        lesson.setTeacherId(30L);
        LocalDateTime reminderAt = LocalDateTime.now().plusHours(1);
        lesson.setLessonDate(reminderAt.toLocalDate());
        lesson.setStartTime(reminderAt.toLocalTime());
        lesson.setStatus(1);

        ClassStudent enrollment = new ClassStudent();
        enrollment.setClassId(20L);
        enrollment.setStudentId(40L);
        enrollment.setStatus(1);

        ParentStudent binding = new ParentStudent();
        binding.setStudentId(40L);
        binding.setParentUserId(50L);

        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(lesson));
        when(classStudentMapper.selectList(any())).thenReturn(List.of(enrollment));
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(binding));
        reminderService.sendLessonReminders();

        verify(notificationService, times(2)).sendOnce(any(), any(), any());
    }

    @Test
    @DisplayName("课时即将到期时提醒绑定家长")
    void sendLessonExpiryReminders() {
        LessonAccount account = new LessonAccount();
        account.setId(1L);
        account.setStudentId(40L);
        account.setCourseId(60L);
        account.setRemainingLessons(new BigDecimal("2"));
        account.setExpireDate(LocalDate.now().plusDays(5));

        ParentStudent binding = new ParentStudent();
        binding.setStudentId(40L);
        binding.setParentUserId(50L);

        when(lessonAccountMapper.selectList(any())).thenReturn(List.of(account));
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(binding));
        reminderService.sendLessonExpiryReminders();

        verify(notificationService).sendOnce(any(), any(), any());
    }

    @Test
    @DisplayName("已过期课时账户不发送即将到期提醒")
    void sendLessonExpiryReminders_skipsExpiredAccount() {
        LessonAccount account = new LessonAccount();
        account.setId(2L);
        account.setStudentId(40L);
        account.setRemainingLessons(new BigDecimal("2"));
        account.setExpireDate(LocalDate.now().minusDays(1));

        when(lessonAccountMapper.selectList(any())).thenReturn(List.of(account));

        reminderService.sendLessonExpiryReminders();

        verifyNoInteractions(notificationService);
    }
}
