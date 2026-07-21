package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
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

/**
 * 课时账户扣减单元测试（通过 AttendanceServiceImpl.submit 触发）。
 * 对应 docs/11-后端开发详细文档.md §11：覆盖并发扣减乐观锁重试、余额不足边界。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("课时账户扣减单元测试")
class LessonAccountServiceTest {

    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private LessonAccountMapper lessonAccountMapper;
    @Mock private LessonFlowMapper lessonFlowMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @BeforeAll
    static void initLambdaCache() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), LessonAccount.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(2L, "teacher1", "TEACHER"));
        // Mock enrollment check: student is enrolled in the class (status=1)
        lenient().when(classStudentMapper.selectCount(any())).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("正常扣减：余额充足，乐观锁版本号递增")
    void shouldDeductAndIncrementVersion() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(2L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(1L); attendance.setStudentId(1L);
        attendance.setStatus(1); attendance.setDeductLessons(BigDecimal.ONE);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        ClassGroup cg = new ClassGroup();
        cg.setCourseId(5L);
        when(classGroupMapper.selectById(1L)).thenReturn(cg);

        LessonAccount account = new LessonAccount();
        account.setId(1L); account.setStudentId(1L);
        account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.valueOf(10));
        account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        when(lessonAccountMapper.update(any(), any())).thenReturn(1);

        Attendance result = attendanceService.submit(attendance);
        assertThat(result.getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("乐观锁冲突：updateById 返回 0 应抛异常")
    void shouldThrowOnOptimisticLockFailure() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(2L); lesson.setTeacherId(2L); lesson.setClassId(2L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(2L); attendance.setStudentId(2L);
        attendance.setStatus(1); attendance.setDeductLessons(BigDecimal.valueOf(2));

        when(scheduleLessonMapper.selectById(2L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        ClassGroup cg = new ClassGroup();
        cg.setCourseId(5L);
        when(classGroupMapper.selectById(2L)).thenReturn(cg);

        LessonAccount account = new LessonAccount();
        account.setId(2L); account.setStudentId(2L);
        account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.valueOf(5));
        account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        when(lessonAccountMapper.update(any(), any())).thenReturn(0);

        assertThatThrownBy(() -> attendanceService.submit(attendance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课时账户更新冲突");
    }

    @Test
    @DisplayName("无课时账户时应阻止扣减")
    void shouldRejectWhenNoAccount() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(3L); lesson.setTeacherId(2L); lesson.setClassId(3L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(3L); attendance.setStudentId(3L);
        attendance.setStatus(1); attendance.setDeductLessons(BigDecimal.ONE);

        when(scheduleLessonMapper.selectById(3L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        ClassGroup cg = new ClassGroup();
        cg.setCourseId(5L);
        when(classGroupMapper.selectById(3L)).thenReturn(cg);
        when(lessonAccountMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> attendanceService.submit(attendance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无课时账户");
    }

    @Test
    @DisplayName("请假不扣课时")
    void shouldNotDeductForLeave() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(4L); lesson.setTeacherId(2L); lesson.setClassId(4L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(4L); attendance.setStudentId(4L);
        attendance.setStatus(2); // 请假
        attendance.setDeductLessons(BigDecimal.ZERO);

        when(scheduleLessonMapper.selectById(4L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        Attendance result = attendanceService.submit(attendance);
        assertThat(result.getStatus()).isEqualTo(2);
        verify(lessonAccountMapper, never()).selectOne(any());
    }
}
