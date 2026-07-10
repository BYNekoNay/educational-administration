package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
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

/**
 * 考勤提交与课时扣减单元测试。
 * 对应 docs/11-后端开发详细文档.md §11：覆盖重复提交回冲、余额为0边界用例。
 * 注意：insert() 的返回值在 submit() 中未使用，因此不 stub insert 返回值，
 * 避免了 MyBatis-Plus BaseMapper 中 insert(T) 与 insert(Collection<T>) 的重载歧义。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("考勤与课时扣减单元测试")
class AttendanceServiceTest {

    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private LessonAccountMapper lessonAccountMapper;
    @Mock private LessonFlowMapper lessonFlowMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(2L, "teacher1", "TEACHER"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    @Test
    @DisplayName("课时余额为0时应阻止到课考勤提交")
    void shouldRejectAttendanceWhenBalanceZero() {
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
        account.setId(1L); account.setStudentId(1L); account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.ZERO); account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);

        assertThatThrownBy(() -> attendanceService.submit(attendance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课时余额为0");
    }

    @Test
    @DisplayName("余额不足但大于0时允许扣至0")
    void shouldDeductToZeroWhenBalanceInsufficient() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(2L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(1L); attendance.setStudentId(1L);
        attendance.setStatus(1); attendance.setDeductLessons(BigDecimal.valueOf(3));

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        ClassGroup cg = new ClassGroup();
        cg.setCourseId(5L);
        when(classGroupMapper.selectById(1L)).thenReturn(cg);

        LessonAccount account = new LessonAccount();
        account.setId(1L); account.setStudentId(1L); account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.valueOf(2)); account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        when(lessonAccountMapper.updateById(any(LessonAccount.class))).thenReturn(1);

        Attendance result = attendanceService.submit(attendance);
        assertThat(result.getStudentId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("重复提交应回冲旧考勤后更新")
    void shouldReverseOldAttendanceOnResubmit() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(2L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance newAttendance = new Attendance();
        newAttendance.setLessonId(1L); newAttendance.setStudentId(1L);
        newAttendance.setStatus(2); // 改为请假
        newAttendance.setDeductLessons(BigDecimal.ONE);

        Attendance oldAttendance = new Attendance();
        oldAttendance.setId(10L); oldAttendance.setLessonId(1L);
        oldAttendance.setStudentId(1L); oldAttendance.setStatus(1);
        oldAttendance.setDeductLessons(BigDecimal.ONE);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(oldAttendance);
        when(attendanceMapper.updateById(any(Attendance.class))).thenReturn(1);

        ClassGroup cg = new ClassGroup();
        cg.setCourseId(5L);
        when(classGroupMapper.selectById(1L)).thenReturn(cg);

        LessonAccount account = new LessonAccount();
        account.setId(1L); account.setStudentId(1L); account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.valueOf(10)); account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        when(lessonAccountMapper.updateById(any(LessonAccount.class))).thenReturn(1);

        Attendance result = attendanceService.submit(newAttendance);
        assertThat(result.getStatus()).isEqualTo(2);
    }

    @Test
    @DisplayName("课次状态不允许考勤时应拒绝")
    void shouldRejectWhenLessonStatusInvalid() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(2L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(3); // 已取消

        Attendance attendance = new Attendance();
        attendance.setLessonId(1L); attendance.setStudentId(1L);
        attendance.setStatus(1);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> attendanceService.submit(attendance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态不允许");
    }

    @Test
    @DisplayName("教师不能操作非自己课次的考勤")
    void shouldRejectNonOwnLesson() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(99L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance attendance = new Attendance();
        attendance.setLessonId(1L); attendance.setStudentId(1L);
        attendance.setStatus(1);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> attendanceService.submit(attendance))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于您");
    }
}
