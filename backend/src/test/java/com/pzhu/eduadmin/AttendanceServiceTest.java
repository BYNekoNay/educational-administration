package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.mapper.LeaveRequestMapper;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
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
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
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
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private StudentMapper studentMapper;
    @Mock private LeaveRequestMapper leaveRequestMapper;

    @InjectMocks
    private AttendanceServiceImpl attendanceService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Attendance.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(assistant, LessonAccount.class);
        TableInfoHelper.initTableInfo(assistant, LessonFlow.class);
        TableInfoHelper.initTableInfo(assistant, ClassGroup.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(2L, "teacher1", "TEACHER"));
        // Mock enrollment check: student is enrolled in the class (status=1)
        lenient().when(classStudentMapper.selectCount(any())).thenReturn(1L);
        lenient().when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");
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
        lenient().when(classGroupMapper.selectById(1L)).thenReturn(cg);

        LessonAccount account = new LessonAccount();
        account.setId(1L); account.setStudentId(1L); account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.valueOf(10)); account.setVersion(0);
        lenient().when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        lenient().when(lessonAccountMapper.updateById(any(LessonAccount.class))).thenReturn(1);

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

    // ==================== batchSubmit ====================

    @Test
    @DisplayName("batchSubmit - 批量提交多个学员考勤")
    void batchSubmit_multipleStudents_shouldDelegateToSubmit() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L); lesson.setTeacherId(2L); lesson.setClassId(1L);
        lesson.setClassroomId(1L); lesson.setStatus(1);

        Attendance a1 = new Attendance();
        a1.setStudentId(10L); a1.setStatus(1); a1.setDeductLessons(BigDecimal.ONE);

        Attendance a2 = new Attendance();
        a2.setStudentId(20L); a2.setStatus(2); a2.setDeductLessons(BigDecimal.ZERO);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(attendanceMapper.selectOne(any())).thenReturn(null);

        ClassGroup cg = new ClassGroup(); cg.setCourseId(5L);
        lenient().when(classGroupMapper.selectById(1L)).thenReturn(cg);

        // a1 is status=1 so deductLessons will be called
        LessonAccount account = new LessonAccount();
        account.setId(1L); account.setStudentId(10L); account.setCourseId(5L);
        account.setRemainingLessons(BigDecimal.TEN); account.setVersion(0);
        when(lessonAccountMapper.selectOne(any())).thenReturn(account);
        when(lessonAccountMapper.updateById(any(LessonAccount.class))).thenReturn(1);

        // populateAttendanceNames
        lenient().when(studentMapper.selectNamesByIdsIncludeDeleted(any())).thenReturn(List.of());
        lenient().when(scheduleLessonMapper.selectByIdsIncludeDeleted(any())).thenReturn(List.of());

        List<Attendance> result = attendanceService.batchSubmit(1L, List.of(a1, a2));

        assertThat(result).hasSize(2);
        // lessonId should be set from batchSubmit parameter
        assertThat(result.get(0).getLessonId()).isEqualTo(1L);
        assertThat(result.get(1).getLessonId()).isEqualTo(1L);
        verify(attendanceMapper, times(2)).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("batchSubmit - 空列表不触发任何操作")
    void batchSubmit_emptyList_noOp() {
        List<Attendance> result = attendanceService.batchSubmit(1L, Collections.emptyList());

        assertThat(result).isEmpty();
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    // ==================== page ====================

    @Test
    @DisplayName("page - 返回分页考勤记录并填充姓名")
    void page_returnsPaginatedRecords_withNames() {
        Attendance att = new Attendance();
        att.setId(1L);
        att.setStudentId(10L);
        att.setLessonId(101L);

        Page<Attendance> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(att));
        mockPage.setTotal(1);

        when(attendanceMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(studentMapper.selectNamesByIdsIncludeDeleted(any()))
                .thenReturn(List.of(Map.of("id", 10L, "name", "张三")));

        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(101L); sl.setClassId(5L);
        sl.setLessonDate(LocalDate.of(2026, 7, 1));
        sl.setStartTime(LocalTime.of(9, 0));
        when(scheduleLessonMapper.selectByIdsIncludeDeleted(any())).thenReturn(List.of(sl));
        when(classGroupMapper.selectClassNamesByIdsIncludeDeleted(any()))
                .thenReturn(List.of(Map.of("id", 5L, "class_name", "钢琴班A")));

        Page<Attendance> result = attendanceService.page(1, 10, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("张三");
        assertThat(result.getRecords().get(0).getLessonInfo()).contains("钢琴班A");
    }

    @Test
    @DisplayName("page - 空记录不触发名称填充")
    void page_emptyRecords_noNamePopulation() {
        Page<Attendance> mockPage = new Page<>(1, 10);
        mockPage.setRecords(Collections.emptyList());

        when(attendanceMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<Attendance> result = attendanceService.page(1, 10, "checkTime", "desc");

        assertThat(result.getRecords()).isEmpty();
        verify(studentMapper, never()).selectNamesByIdsIncludeDeleted(any());
    }

    // ==================== getById ====================

    @Test
    @DisplayName("getById - 存在时返回记录")
    void getById_found_returnsRecord() {
        Attendance att = new Attendance();
        att.setId(5L);
        att.setStudentId(10L);

        when(attendanceMapper.selectById(5L)).thenReturn(att);

        Attendance result = attendanceService.getById(5L);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getById - 不存在时返回null")
    void getById_notFound_returnsNull() {
        when(attendanceMapper.selectById(999L)).thenReturn(null);

        Attendance result = attendanceService.getById(999L);

        assertThat(result).isNull();
    }

    // ==================== getLessonStudents ====================

    @Test
    @DisplayName("getLessonStudents - 返回课次对应班级的学员列表")
    void getLessonStudents_returnsClassStudents() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L);
        lesson.setClassId(10L);

        ClassStudent cs1 = new ClassStudent();
        cs1.setStudentId(100L);
        ClassStudent cs2 = new ClassStudent();
        cs2.setStudentId(200L);

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs1, cs2));
        when(studentMapper.selectNamesByIdsIncludeDeleted(any())).thenReturn(List.of(
                Map.of("id", 100L, "name", "张三"),
                Map.of("id", 200L, "name", "李四")));

        List<ClassStudent> result = attendanceService.getLessonStudents(1L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ClassStudent::getStudentName).containsExactly("张三", "李四");
    }

    @Test
    @DisplayName("getLessonStudents - 课次不存在时抛异常")
    void getLessonStudents_lessonNotFound_throwsException() {
        when(scheduleLessonMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> attendanceService.getLessonStudents(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课次不存在");
    }

    // ==================== getByLessonId ====================

    @Test
    @DisplayName("getByLessonId - 返回指定课次的考勤记录")
    void getByLessonId_returnsAttendanceRecords() {
        Attendance att1 = new Attendance();
        att1.setId(1L); att1.setLessonId(10L); att1.setStudentId(100L);
        Attendance att2 = new Attendance();
        att2.setId(2L); att2.setLessonId(10L); att2.setStudentId(200L);

        when(attendanceMapper.selectList(any())).thenReturn(List.of(att1, att2));

        List<Attendance> result = attendanceService.getByLessonId(10L);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(a -> a.getLessonId().equals(10L));
    }

    // ==================== pageByStudentId ====================

    @Test
    @DisplayName("pageByStudentId - 返回指定学员的分页考勤记录")
    void pageByStudentId_returnsPaginatedRecords() {
        Attendance att = new Attendance();
        att.setId(1L); att.setStudentId(10L); att.setLessonId(101L);

        Page<Attendance> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(att));
        mockPage.setTotal(1);

        when(attendanceMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);
        when(studentMapper.selectNamesByIdsIncludeDeleted(any()))
                .thenReturn(List.of(Map.of("id", 10L, "name", "李四")));
        when(scheduleLessonMapper.selectByIdsIncludeDeleted(any())).thenReturn(List.of());

        Page<Attendance> result = attendanceService.pageByStudentId(10L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("李四");
    }

    // ==================== getStudentSchedules ====================

    @Test
    @DisplayName("getStudentSchedules - 返回学员所在班级的课次列表")
    void getStudentSchedules_returnsSchedules() {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(5L); cs.setStudentId(10L); cs.setStatus(1);

        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(1L); sl.setClassId(5L); sl.setStatus(1);
        sl.setLessonDate(LocalDate.of(2026, 7, 5));

        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs));
        when(scheduleLessonMapper.selectList(any())).thenReturn(List.of(sl));

        List<ScheduleLesson> result = attendanceService.getStudentSchedules(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getClassId()).isEqualTo(5L);
    }

    @Test
    @DisplayName("getStudentSchedules - 学员无在班记录时返回空列表")
    void getStudentSchedules_noEnrollments_returnsEmpty() {
        when(classStudentMapper.selectList(any())).thenReturn(Collections.emptyList());

        List<ScheduleLesson> result = attendanceService.getStudentSchedules(10L);

        assertThat(result).isEmpty();
        verify(scheduleLessonMapper, never()).selectList(any());
    }

    // ==================== pageTeacherLessons ====================

    @Test
    @DisplayName("pageTeacherLessons - 返回教师的分页课次")
    void pageTeacherLessons_returnsPaginatedLessons() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(1L); sl.setTeacherId(2L); sl.setStatus(1);

        Page<ScheduleLesson> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(sl));
        mockPage.setTotal(1);

        when(scheduleLessonMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<ScheduleLesson> result = attendanceService.pageTeacherLessons(2L, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getTeacherId()).isEqualTo(2L);
    }

    // ==================== checkTeacherLessonOwnership(Long) ====================

    @Test
    @DisplayName("checkTeacherLessonOwnership(Long) - 课次属于当前教师时不抛异常")
    void checkTeacherLessonOwnershipById_ownedLesson_noException() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L);
        lesson.setTeacherId(2L); // CurrentUserHolder has userId=2

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        attendanceService.checkTeacherLessonOwnership(1L);

        verify(scheduleLessonMapper).selectById(1L);
    }

    @Test
    @DisplayName("checkTeacherLessonOwnership(Long) - 课次不存在时抛异常")
    void checkTeacherLessonOwnershipById_notFound_throwsException() {
        when(scheduleLessonMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> attendanceService.checkTeacherLessonOwnership(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课次不存在");
    }

    @Test
    @DisplayName("checkTeacherLessonOwnership(Long) - 课次不属于当前教师时抛异常")
    void checkTeacherLessonOwnershipById_notOwned_throwsException() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L);
        lesson.setTeacherId(99L); // NOT userId=2

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> attendanceService.checkTeacherLessonOwnership(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不属于您");
    }
}
