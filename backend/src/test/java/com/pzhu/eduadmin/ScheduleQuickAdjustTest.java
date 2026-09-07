package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.schedule.dto.QuickAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.Period;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ClassroomMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.PeriodMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.RoomBookingMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleAdjustRequestMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleConflictService;
import com.pzhu.eduadmin.modules.schedule.service.ScheduleServiceImpl;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P1 教务快速调课（拖拽落库）Mock 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("排课快速调课 Mock 单元测试")
class ScheduleQuickAdjustTest {

    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private ClassroomMapper classroomMapper;
    @Mock private RoomBookingMapper roomBookingMapper;
    @Mock private ScheduleAdjustRequestMapper scheduleAdjustRequestMapper;
    @Mock private ScheduleConflictService scheduleConflictService;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private NotificationService notificationService;
    @Mock private AttendanceService attendanceService;
    @Mock private PeriodMapper periodMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ParentStudentMapper parentStudentMapper;

    @InjectMocks private ScheduleServiceImpl scheduleService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(asst, Classroom.class);
        TableInfoHelper.initTableInfo(asst, ClassStudent.class);
        TableInfoHelper.initTableInfo(asst, ParentStudent.class);
        TableInfoHelper.initTableInfo(asst, ClassGroup.class);
        TableInfoHelper.initTableInfo(asst, User.class);
        TableInfoHelper.initTableInfo(asst, Course.class);
        TableInfoHelper.initTableInfo(asst, Period.class);
        TableInfoHelper.initTableInfo(asst, RoomBooking.class);
        TableInfoHelper.initTableInfo(asst, ScheduleAdjustRequest.class);
    }

    // ---------- 工具 ----------

    private ScheduleLesson futureLesson(long id, Long classId, Long teacherId, Long roomId,
                                        LocalDate date, String start, String end) {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(id);
        lesson.setClassId(classId);
        lesson.setTeacherId(teacherId);
        lesson.setClassroomId(roomId);
        lesson.setLessonDate(date);
        lesson.setStartTime(LocalTime.parse(start));
        lesson.setEndTime(LocalTime.parse(end));
        lesson.setStatus(1);
        return lesson;
    }

    private QuickAdjustRequest request(String date, String start, String end, String reason) {
        QuickAdjustRequest req = new QuickAdjustRequest();
        req.setLessonDate(LocalDate.parse(date));
        req.setStartTime(LocalTime.parse(start));
        req.setEndTime(LocalTime.parse(end));
        req.setReason(reason);
        return req;
    }

    private Classroom activeClassroom() {
        Classroom c = new Classroom();
        c.setId(2L);
        c.setStatus(1);
        return c;
    }

    private void stubNameResolution() {
        ClassGroup cg = new ClassGroup();
        cg.setId(5L);
        cg.setCourseId(100L);
        cg.setClassName("钢琴A班");
        when(classGroupMapper.selectBatchIds(anyCollection())).thenReturn(List.of(cg));

        User teacher = new User();
        teacher.setId(3L);
        teacher.setRealName("张老师");
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(List.of(teacher));

        Classroom room = new Classroom();
        room.setId(2L);
        room.setName("101教室");
        when(classroomMapper.selectBatchIds(anyCollection())).thenReturn(List.of(room));

        Map<String, Object> courseRow = new HashMap<>();
        courseRow.put("id", 100L);
        courseRow.put("name", "钢琴");
        when(courseMapper.selectNamesByIdsIncludeDeleted(anyCollection())).thenReturn(List.of(courseRow));
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("成功路径：直接更新原课次时间，通知教师与家长，写操作日志")
    void quickAdjust_success() {
        LocalDate today = LocalDate.now();
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        ScheduleLesson updated = futureLesson(1L, 5L, 3L, 2L, today.plusDays(3), "14:00", "15:30");

        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson, updated);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        stubNameResolution();

        // 班级在班学员 11、12 → 家长 20（关联两名学员，去重）、21
        ClassStudent cs11 = new ClassStudent(); cs11.setStudentId(11L);
        ClassStudent cs12 = new ClassStudent(); cs12.setStudentId(12L);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs11, cs12));
        ParentStudent ps1 = new ParentStudent(); ps1.setParentUserId(20L); ps1.setStudentId(11L);
        ParentStudent ps2 = new ParentStudent(); ps2.setParentUserId(20L); ps2.setStudentId(12L);
        ParentStudent ps3 = new ParentStudent(); ps3.setParentUserId(21L); ps3.setStudentId(11L);
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(ps1, ps2, ps3));

        ScheduleLesson result = scheduleService.quickAdjustLesson(1L,
                request(today.plusDays(3).toString(), "14:00", "15:30", "加课排练"));

        assertThat(result.getLessonDate()).isEqualTo(today.plusDays(3));
        assertThat(result.getStartTime()).isEqualTo(LocalTime.parse("14:00"));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.parse("15:30"));
        // 语义：不置 4、不生成 sourceLessonId（直接更新原课次）
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getSourceLessonId()).isNull();

        verify(scheduleLessonMapper).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(scheduleConflictService).checkConflict(any(ScheduleLesson.class));
        // 教师 + 家长通知
        verify(notificationService).send(eq(3L), any(Notification.class));
        ArgumentCaptor<java.util.List<Long>> parentCaptor = ArgumentCaptor.forClass(java.util.List.class);
        verify(notificationService).sendToUsers(parentCaptor.capture(), any(Notification.class));
        Set<Long> parentIds = parentCaptor.getValue().stream().collect(Collectors.toSet());
        assertThat(parentIds).containsExactlyInAnyOrder(20L, 21L);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("非待上课课次禁止快速调课 → 409")
    void quickAdjust_notPendingStatus() {
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L);
        lesson.setStatus(2); // 已完成
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅待上课课次可快速调课");
        verify(scheduleLessonMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("过去日期的课次禁止调课 → 400")
    void quickAdjust_pastLessonRejected() {
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L,
                LocalDate.now().minusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("过去课次不可调课");
    }

    @Test
    @DisplayName("今天且已过开始时间的课次禁止调课 → 409")
    void quickAdjust_startedTodayRejected() {
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L,
                LocalDate.now(), LocalTime.MIN.toString(), "23:59");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已开始");
    }

    @Test
    @DisplayName("目标日期为过去日期 → 400")
    void quickAdjust_targetInPast() {
        LocalDate today = LocalDate.now();
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        QuickAdjustRequest req = request(today.minusDays(1).toString(), "14:00", "15:30", null);
        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标日期不能早于今天");
    }

    @Test
    @DisplayName("结束时间不晚于开始时间 → 400")
    void quickAdjust_invalidTimeRange() {
        LocalDate today = LocalDate.now();
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        QuickAdjustRequest req = request(today.plusDays(3).toString(), "14:00", "14:00", null);
        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("结束时间必须晚于开始时间");
    }

    @Test
    @DisplayName("目标时段存在冲突 → 409 且不落库")
    void quickAdjust_conflictRejected() {
        LocalDate today = LocalDate.now();
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(List.of("教师冲突：该教师已有课次"));

        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L,
                request(today.plusDays(3).toString(), "14:00", "15:30", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("快速调课冲突");
        verify(scheduleLessonMapper, never()).update(isNull(), any(LambdaUpdateWrapper.class));
        verify(notificationService, never()).sendToUsers(any(), any(Notification.class));
    }

    @Test
    @DisplayName("CAS 更新失败（并发） → 409 不通知")
    void quickAdjust_casFailure() {
        LocalDate today = LocalDate.now();
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.update(isNull(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L,
                request(today.plusDays(3).toString(), "14:00", "15:30", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请刷新后重试");
        verify(notificationService, never()).send(eq(3L), any(Notification.class));
        verify(notificationService, never()).sendToUsers(any(), any(Notification.class));
    }

    @Test
    @DisplayName("并发守卫：同一课次两笔快速调课，第二笔旧快照 CAS 时间不匹配 → 409 且不重复通知")
    void quickAdjust_casLostUpdateSecondAdjustRejected() {
        LocalDate today = LocalDate.now();
        // 两名教务在 T1 提交前都读到同一旧快照（明天 10:00-11:30）
        ScheduleLesson stale = futureLesson(1L, 5L, 3L, 2L, today.plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(stale);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(Collections.emptyList());
        // T1 先提交：DB 行时间仍等于旧快照 → CAS 命中返回 1；
        // T2 随后提交：DB 行时间已被 T1 改动，WHERE 旧时间条件不匹配 → 返回 0
        when(scheduleLessonMapper.update(isNull(), any(LambdaUpdateWrapper.class)))
                .thenReturn(1).thenReturn(0);
        stubNameResolution();
        ClassStudent cs11 = new ClassStudent(); cs11.setStudentId(11L);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs11));
        ParentStudent ps1 = new ParentStudent(); ps1.setParentUserId(20L); ps1.setStudentId(11L);
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(ps1));

        // T1：成功（发一次通知）
        scheduleService.quickAdjustLesson(1L,
                request(today.plusDays(3).toString(), "14:00", "15:30", "教务A调整"));
        verify(notificationService).send(eq(3L), any(Notification.class));
        verify(notificationService).sendToUsers(any(), any(Notification.class));

        // T2：仍以旧快照发起，CAS 因旧时间已不匹配返回 0 → 409，且不再发第二笔通知
        assertThatThrownBy(() -> scheduleService.quickAdjustLesson(1L,
                request(today.plusDays(4).toString(), "09:00", "10:00", "教务B调整")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请刷新后重试");
        verify(notificationService, times(1)).send(eq(3L), any(Notification.class));
        verify(notificationService, times(1)).sendToUsers(any(), any(Notification.class));

        // CAS WHERE 携带旧时间条件：捕获第一次成功更新的 wrapper，
        // 先物化 SQL 段再取参，校验包含旧日期/起止时间（防回退成仅 id+status 的 CAS）
        ArgumentCaptor<LambdaUpdateWrapper<ScheduleLesson>> captor =
                ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
        verify(scheduleLessonMapper, times(2)).update(isNull(), captor.capture());
        LambdaUpdateWrapper<ScheduleLesson> firstWrapper = captor.getAllValues().get(0);
        firstWrapper.getSqlSegment();
        Map<String, Object> firstWrapperParams = firstWrapper.getParamNameValuePairs();
        assertThat(firstWrapperParams.values())
                .contains(today.plusDays(1), LocalTime.parse("10:00"), LocalTime.parse("11:30"), 1, 1L);
    }

    @Test
    @DisplayName("notify-scope：返回教师与在班家长去重数")
    void notifyScope_returnsTeacherAndParentCount() {
        ScheduleLesson lesson = futureLesson(1L, 5L, 3L, 2L,
                LocalDate.now().plusDays(1), "10:00", "11:30");
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);

        User teacher = new User();
        teacher.setId(3L);
        teacher.setRealName("张老师");
        when(userMapper.selectById(3L)).thenReturn(teacher);

        ClassStudent cs11 = new ClassStudent(); cs11.setStudentId(11L);
        ClassStudent cs12 = new ClassStudent(); cs12.setStudentId(12L);
        when(classStudentMapper.selectList(any())).thenReturn(List.of(cs11, cs12));
        ParentStudent ps1 = new ParentStudent(); ps1.setParentUserId(20L); ps1.setStudentId(11L);
        ParentStudent ps2 = new ParentStudent(); ps2.setParentUserId(20L); ps2.setStudentId(12L);
        when(parentStudentMapper.selectList(any())).thenReturn(List.of(ps1, ps2));

        Map<String, Object> scope = scheduleService.getNotifyScope(1L);

        assertThat(scope.get("teacherName")).isEqualTo("张老师");
        assertThat(scope.get("parentCount")).isEqualTo(1);
    }
}
