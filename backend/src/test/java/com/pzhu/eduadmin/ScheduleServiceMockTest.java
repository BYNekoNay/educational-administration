package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.*;
import com.pzhu.eduadmin.modules.schedule.dto.AdjustRequestVO;
import com.pzhu.eduadmin.modules.schedule.dto.AutoScheduleRequest;
import com.pzhu.eduadmin.modules.schedule.mapper.*;
import com.pzhu.eduadmin.modules.schedule.service.*;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("排课服务 Mock 单元测试")
class ScheduleServiceMockTest {

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
    @Mock private com.pzhu.eduadmin.modules.attendance.service.AttendanceService attendanceService;

    @InjectMocks
    private ScheduleServiceImpl scheduleService;

    private MockedStatic<QueryHelper> queryHelperMock;
    private MockedStatic<IpUtil> ipUtilMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(asst, Classroom.class);
        TableInfoHelper.initTableInfo(asst, RoomBooking.class);
        TableInfoHelper.initTableInfo(asst, ScheduleAdjustRequest.class);
        TableInfoHelper.initTableInfo(asst, ClassGroup.class);
        TableInfoHelper.initTableInfo(asst, User.class);
        TableInfoHelper.initTableInfo(asst, Course.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
        queryHelperMock = mockStatic(QueryHelper.class);
        ipUtilMock = mockStatic(IpUtil.class);
        ipUtilMock.when(IpUtil::getCurrentIp).thenReturn("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
        if (queryHelperMock != null) queryHelperMock.close();
        if (ipUtilMock != null) ipUtilMock.close();
    }

    // ============ 课次 ============

    @Test
    @DisplayName("智能排课按星期和教室容量自动生成无冲突课次")
    void autoSchedule_Success() {
        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(10L);
        classGroup.setMaxStudentCount(10);

        User teacher = new User();
        teacher.setId(20L);
        teacher.setRoleCode("TEACHER");
        teacher.setStatus(1);

        Classroom smallRoom = new Classroom();
        smallRoom.setId(1L);
        smallRoom.setCapacity(5);
        smallRoom.setStatus(1);
        Classroom suitableRoom = new Classroom();
        suitableRoom.setId(2L);
        suitableRoom.setCapacity(20);
        suitableRoom.setStatus(1);

        AutoScheduleRequest request = new AutoScheduleRequest();
        request.setClassId(10L);
        request.setTeacherId(20L);
        request.setStartDate(LocalDate.of(2026, 7, 6));
        request.setEndDate(LocalDate.of(2026, 7, 20));
        request.setStartTime(LocalTime.of(9, 0));
        request.setEndTime(LocalTime.of(10, 0));
        request.setLessonCount(2);
        request.setWeekdays(List.of(DayOfWeek.MONDAY.getValue()));

        when(classGroupMapper.selectById(10L)).thenReturn(classGroup);
        when(scheduleLessonMapper.lockAutoSchedule()).thenReturn(1L);
        when(userMapper.selectById(20L)).thenReturn(teacher);
        when(classroomMapper.selectList(any())).thenReturn(List.of(smallRoom, suitableRoom));
        when(scheduleConflictService.checkConflict(any())).thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.insert(any(ScheduleLesson.class))).thenReturn(1);

        List<ScheduleLesson> result = scheduleService.autoSchedule(request);

        assertThat(result).hasSize(2);
        assertThat(result).allMatch(lesson -> lesson.getClassroomId().equals(2L));
        assertThat(result).allMatch(lesson -> lesson.getLessonDate().getDayOfWeek() == DayOfWeek.MONDAY);
        verify(scheduleLessonMapper, times(2)).insert(any(ScheduleLesson.class));
        verify(scheduleLessonMapper).lockAutoSchedule();
    }

    @Test
    @DisplayName("智能排课事务锁缺失时拒绝继续排课")
    void autoSchedule_MissingLockFailsClosed() {
        when(scheduleLessonMapper.lockAutoSchedule()).thenReturn(null);

        assertThatThrownBy(() -> scheduleService.autoSchedule(new AutoScheduleRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("事务锁未初始化");
        verify(scheduleLessonMapper, never()).insert(any(ScheduleLesson.class));
    }

    @Test
    @DisplayName("分页查询课次 — 含名称填充")
    void pageScheduleLessons_Success() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(1L);
        sl.setClassId(10L);
        sl.setTeacherId(20L);
        sl.setClassroomId(30L);
        Page<ScheduleLesson> mp = new Page<>(1, 10);
        mp.setRecords(List.of(sl));
        when(scheduleLessonMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);
        when(classGroupMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(userMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());
        when(classroomMapper.selectBatchIds(anyCollection())).thenReturn(Collections.emptyList());

        Page<ScheduleLesson> result = scheduleService.pageScheduleLessons(1, 10, null, null, null, null, null, null, null, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("按 ID 查询课次")
    void getLessonById_Found() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(1L);
        when(scheduleLessonMapper.selectById(1L)).thenReturn(sl);

        assertThat(scheduleService.getLessonById(1L).getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("创建课次无冲突")
    void createLesson_NoConflict() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setLessonDate(LocalDate.of(2026, 7, 20));
        sl.setStartTime(LocalTime.of(14, 0));
        sl.setEndTime(LocalTime.of(15, 30));
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { inv.getArgument(0, ScheduleLesson.class).setId(100L); return 1; })
                .when(scheduleLessonMapper).insert(any(ScheduleLesson.class));

        ScheduleLesson result = scheduleService.createLesson(sl);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("创建课次有冲突 — 抛出 409")
    void createLesson_HasConflict() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setLessonDate(LocalDate.of(2026, 7, 20));
        sl.setStartTime(LocalTime.of(14, 0));
        sl.setEndTime(LocalTime.of(15, 30));
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(List.of("教师时间冲突"));

        assertThatThrownBy(() -> scheduleService.createLesson(sl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排课冲突");
    }

    @Test
    @DisplayName("删除课次并写操作日志")
    void deleteLesson_Success() {
        // High fix 后删除前先校验课次状态，仅待上课（status=1）可删除
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(1L);
        lesson.setStatus(1);
        when(scheduleLessonMapper.selectById(1L)).thenReturn(lesson);
        when(scheduleLessonMapper.deleteById(1L)).thenReturn(1);

        assertThat(scheduleService.deleteLesson(1L)).isTrue();
        verify(operationLogService).log(anyString(), anyString());
    }

    // ============ 更新课次 ============

    @Test
    @DisplayName("更新课次无冲突")
    void updateLesson_NoConflict() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(100L);
        sl.setClassId(10L);
        sl.setLessonDate(LocalDate.of(2026, 7, 20));
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class))).thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.updateById(any(ScheduleLesson.class))).thenReturn(1);
        when(scheduleLessonMapper.selectById(100L)).thenReturn(sl);

        ScheduleLesson result = scheduleService.updateLesson(sl);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("更新课次有冲突 — 抛出 409")
    void updateLesson_HasConflict() {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setId(100L);
        // H6 fix: updateLesson 现在先加载现有记录再合并
        ScheduleLesson existing = new ScheduleLesson();
        existing.setId(100L);
        existing.setTeacherId(1L);
        existing.setClassId(1L);
        when(scheduleLessonMapper.selectById(100L)).thenReturn(existing);
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(List.of("教室时间冲突"));

        assertThatThrownBy(() -> scheduleService.updateLesson(sl))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排课冲突");
    }

    // ============ 冲突检查(委托) ============

    @Test
    @DisplayName("checkConflict 委托给 scheduleConflictService")
    void checkConflict_Delegates() {
        ScheduleLesson sl = new ScheduleLesson();
        when(scheduleConflictService.checkConflict(sl)).thenReturn(List.of("教师冲突"));

        List<String> result = scheduleService.checkConflict(sl);

        assertThat(result).containsExactly("教师冲突");
        verify(scheduleConflictService).checkConflict(sl);
    }

    // ============ 教室更多方法 ============

    @Test
    @DisplayName("分页查询教室")
    void pageClassrooms_Success() {
        Classroom room = new Classroom();
        room.setId(1L);
        room.setName("101");
        Page<Classroom> mp = new Page<>(1, 10);
        mp.setRecords(List.of(room));
        when(classroomMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<Classroom> result = scheduleService.pageClassrooms(1, 10, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("创建教室名称不能为空")
    void createClassroom_NameBlank() {
        Classroom room = new Classroom();
        room.setName("");

        assertThatThrownBy(() -> scheduleService.createClassroom(room))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教室名称不能为空");
    }

    @Test
    @DisplayName("创建教室容量必须大于0")
    void createClassroom_CapacityInvalid() {
        Classroom room = new Classroom();
        room.setName("101");
        room.setCapacity(0);

        assertThatThrownBy(() -> scheduleService.createClassroom(room))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("容量必须大于0");
    }

    @Test
    @DisplayName("正常创建教室")
    void createClassroom_Success() {
        Classroom room = new Classroom();
        room.setName("101");
        room.setCapacity(30);
        doAnswer(inv -> { inv.getArgument(0, Classroom.class).setId(50L); return 1; })
                .when(classroomMapper).insert(any(Classroom.class));

        Classroom result = scheduleService.createClassroom(room);

        assertThat(result.getId()).isEqualTo(50L);
    }

    @Test
    @DisplayName("删除教室写日志")
    void deleteClassroom_Success() {
        when(classroomMapper.deleteById(1L)).thenReturn(1);

        assertThat(scheduleService.deleteClassroom(1L)).isTrue();
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("按 ID 查询教室存在")
    void getClassroomById_Found() {
        Classroom room = new Classroom();
        room.setId(1L);
        room.setName("101");
        when(classroomMapper.selectById(1L)).thenReturn(room);

        assertThat(scheduleService.getClassroomById(1L).getName()).isEqualTo("101");
    }

    @Test
    @DisplayName("按 ID 查询教室不存在 — 返回 null")
    void getClassroomById_NotFound() {
        when(classroomMapper.selectById(999L)).thenReturn(null);

        assertThat(scheduleService.getClassroomById(999L)).isNull();
    }

    @Test
    @DisplayName("更新教室成功并重查")
    void updateClassroom_Success() {
        Classroom room = new Classroom();
        room.setId(1L);
        room.setName("新名称");
        when(classroomMapper.updateById(any(Classroom.class))).thenReturn(1);
        when(classroomMapper.selectById(1L)).thenReturn(room);

        Classroom result = scheduleService.updateClassroom(room);

        assertThat(result.getName()).isEqualTo("新名称");
    }

    // ============ 教室预约 ============

    @Test
    @DisplayName("创建教室预约")
    void createRoomBooking_Success() {
        RoomBooking rb = new RoomBooking();
        // C3 fix: 必须提供 classroomId、startTime、endTime
        rb.setClassroomId(1L);
        rb.setStartTime(java.time.LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0));
        rb.setEndTime(java.time.LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0));
        // L4 fix: 校验教室存在；r19: 教室须为启用状态(status=1)
        Classroom activeRoom = new Classroom();
        activeRoom.setStatus(1);
        when(classroomMapper.selectById(1L)).thenReturn(activeRoom);
        // M8 fix: 预约对称检查已排课次（无重叠）
        when(scheduleLessonMapper.selectList(any())).thenReturn(Collections.emptyList());
        // C3 fix: 冲突检查 + 插入后二次校验
        when(roomBookingMapper.selectCount(any())).thenReturn(0L);
        doAnswer(inv -> { inv.getArgument(0, RoomBooking.class).setId(1L); return 1; })
                .when(roomBookingMapper).insert(any(RoomBooking.class));

        RoomBooking result = scheduleService.createRoomBooking(rb);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("分页查询教室预约")
    void pageRoomBookings_Success() {
        RoomBooking rb = new RoomBooking();
        rb.setId(1L);
        Page<RoomBooking> mp = new Page<>(1, 10);
        mp.setRecords(List.of(rb));
        when(roomBookingMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<RoomBooking> result = scheduleService.pageRoomBookings(1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    // ============ 调课申请 ============

    @Test
    @DisplayName("教师分页查询调课申请")
    void pageTeacherAdjustRequests_Success() {
        Page<AdjustRequestVO> mp = new Page<>(1, 10);
        when(scheduleAdjustRequestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(new Page<>(1, 10));

        Page<AdjustRequestVO> result = scheduleService.pageTeacherAdjustRequests(1L, 1, 10);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("管理端分页查询调课申请")
    void pageAdjustRequests_Success() {
        when(scheduleAdjustRequestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(new Page<>(1, 10));

        Page<AdjustRequestVO> result = scheduleService.pageAdjustRequests(1, 10, null);

        assertThat(result.getRecords()).size().isZero();
    }

    @Test
    @DisplayName("创建调课申请")
    void createAdjustRequest_Success() {
        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setLessonId(10L);
        // M9 fix: 需要校验课次存在；L fix: 仅待上课(status=1)课次可发起调课
        ScheduleLesson adjustLesson = new ScheduleLesson();
        adjustLesson.setStatus(1);
        when(scheduleLessonMapper.selectById(10L)).thenReturn(adjustLesson);
        doAnswer(inv -> { inv.getArgument(0, ScheduleAdjustRequest.class).setId(1L); return 1; })
                .when(scheduleAdjustRequestMapper).insert(any(ScheduleAdjustRequest.class));

        ScheduleAdjustRequest result = scheduleService.createAdjustRequest(req);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("审核调课申请不存在 — 抛出 404")
    void auditAdjustRequest_NotFound() {
        when(scheduleAdjustRequestMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> scheduleService.auditAdjustRequest(99L, 2, 1L, "通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调课申请不存在");
    }

    @Test
    @DisplayName("审核调课已处理 — 抛出 409")
    void auditAdjustRequest_AlreadyProcessed() {
        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setId(1L);
        req.setStatus(2); // already approved
        when(scheduleAdjustRequestMapper.selectById(1L)).thenReturn(req);

        assertThatThrownBy(() -> scheduleService.auditAdjustRequest(1L, 2, 1L, "通过"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已处理");
    }

    @Test
    @DisplayName("审核驳回调课申请")
    void auditAdjustRequest_Reject() {
        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setId(1L);
        req.setStatus(1);
        req.setLessonId(10L);
        req.setExpectTime(LocalDateTime.now());
        when(scheduleAdjustRequestMapper.selectById(1L)).thenReturn(req);
        when(scheduleAdjustRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ScheduleAdjustRequest result = scheduleService.auditAdjustRequest(1L, 3, 1L, "时间不合适");

        assertThat(result.getStatus()).isEqualTo(3);
        verify(scheduleLessonMapper, never()).insert(any(ScheduleLesson.class));
    }

    @Test
    @DisplayName("审核通过调课 — 创建新课次、旧课次置为4、写日志")
    void auditAdjustRequest_Approve() {
        long reqId = 1L;
        long oldLessonId = 10L;
        long newLessonId = 200L;

        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setId(reqId);
        req.setStatus(1);
        req.setLessonId(oldLessonId);
        req.setExpectTime(LocalDateTime.of(2026, 7, 25, 14, 0));
        when(scheduleAdjustRequestMapper.selectById(reqId)).thenReturn(req);
        when(scheduleAdjustRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ScheduleLesson oldLesson = new ScheduleLesson();
        oldLesson.setId(oldLessonId);
        oldLesson.setClassId(5L);
        oldLesson.setTeacherId(3L);
        oldLesson.setClassroomId(2L);
        oldLesson.setStatus(1);
        oldLesson.setStartTime(LocalTime.of(14, 0));
        oldLesson.setEndTime(LocalTime.of(15, 30));
        when(scheduleLessonMapper.selectById(oldLessonId)).thenReturn(oldLesson);
        when(scheduleLessonMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());

        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class))).thenReturn(Collections.emptyList());
        doAnswer(inv -> { inv.getArgument(0, ScheduleLesson.class).setId(newLessonId); return 1; })
                .when(scheduleLessonMapper).insert(any(ScheduleLesson.class));

        ScheduleAdjustRequest result = scheduleService.auditAdjustRequest(reqId, 2, 1L, "同意调课");

        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getAuditRemark()).isEqualTo("同意调课");

        ArgumentCaptor<ScheduleLesson> newLessonCaptor = ArgumentCaptor.forClass(ScheduleLesson.class);
        verify(scheduleLessonMapper).insert(newLessonCaptor.capture());
        ScheduleLesson newLesson = newLessonCaptor.getValue();
        assertThat(newLesson.getClassId()).isEqualTo(5L);
        assertThat(newLesson.getTeacherId()).isEqualTo(3L);
        assertThat(newLesson.getStatus()).isEqualTo(1);
        assertThat(newLesson.getSourceLessonId()).isEqualTo(oldLessonId);
    }

    @Test
    @DisplayName("审核通过但原课次不存在 — 抛出 404")
    void auditAdjustRequest_ApproveOldLessonGone() {
        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setId(1L);
        req.setStatus(1);
        req.setLessonId(10L);
        req.setExpectTime(LocalDateTime.of(2026, 7, 25, 14, 0));
        when(scheduleAdjustRequestMapper.selectById(1L)).thenReturn(req);
        when(scheduleAdjustRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(scheduleLessonMapper.selectById(10L)).thenReturn(null);

        assertThatThrownBy(() -> scheduleService.auditAdjustRequest(1L, 2, 1L, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("原课次不存在");
    }

    @Test
    @DisplayName("审核通过但新课次有冲突 — 抛出 409")
    void auditAdjustRequest_ApproveNewLessonConflict() {
        ScheduleAdjustRequest req = new ScheduleAdjustRequest();
        req.setId(1L);
        req.setStatus(1);
        req.setLessonId(10L);
        req.setExpectTime(LocalDateTime.of(2026, 7, 25, 14, 0));
        when(scheduleAdjustRequestMapper.selectById(1L)).thenReturn(req);
        when(scheduleAdjustRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ScheduleLesson oldLesson = new ScheduleLesson();
        oldLesson.setId(10L);
        oldLesson.setClassId(5L);
        oldLesson.setTeacherId(3L);
        oldLesson.setClassroomId(2L);
        oldLesson.setStatus(1);
        oldLesson.setStartTime(LocalTime.of(14, 0));
        oldLesson.setEndTime(LocalTime.of(15, 30));
        when(scheduleLessonMapper.selectById(10L)).thenReturn(oldLesson);
        when(scheduleLessonMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(classroomMapper.selectById(2L)).thenReturn(activeClassroom());
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(List.of("教室冲突"));

        assertThatThrownBy(() -> scheduleService.auditAdjustRequest(1L, 2, 1L, "同意"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("调课冲突");
    }

    // ============ 批量排课 ============

    @Test
    @DisplayName("批量排课批次内教师冲突")
    void batchCreate_TeacherConflict() {
        ScheduleLesson a = buildLesson(1L, 1L, 1L, "09:00", "10:00");
        ScheduleLesson b = buildLesson(1L, 1L, 2L, "09:30", "10:30");

        assertThatThrownBy(() -> scheduleService.batchCreate(List.of(a, b)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批次内排课冲突");
    }

    @Test
    @DisplayName("批量排课成功")
    void batchCreate_Success() {
        ScheduleLesson a = buildLesson(null, 1L, 1L, "09:00", "10:00");
        ScheduleLesson b = buildLesson(null, 2L, 2L, "10:00", "11:00");
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.insert(any(ScheduleLesson.class))).thenReturn(1);

        scheduleService.batchCreate(List.of(a, b));

        verify(scheduleLessonMapper, times(2)).insert(any(ScheduleLesson.class));
    }

    private ScheduleLesson buildLesson(Long classId, Long teacherId, Long classroomId, String start, String end) {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setClassId(classId);
        sl.setTeacherId(teacherId);
        sl.setClassroomId(classroomId);
        sl.setLessonDate(LocalDate.of(2026, 7, 20));
        sl.setStartTime(LocalTime.parse(start));
        sl.setEndTime(LocalTime.parse(end));
        return sl;
    }

    @Test
    @DisplayName("与已有课次冲突时抛出异常")
    void batchCreate_ExternalConflict() {
        ScheduleLesson a = buildLesson(null, 1L, 1L, "09:00", "10:00");
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class)))
                .thenReturn(List.of("教师时间冲突"));

        assertThatThrownBy(() -> scheduleService.batchCreate(List.of(a)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("批量排课存在冲突");
    }

    @Test
    @DisplayName("批量排课批次内教室冲突 — 不同教师同一教室同一时段")
    void batchCreate_ClassroomConflict() {
        ScheduleLesson a = buildLesson(null, 1L, 1L, "09:00", "10:00");
        ScheduleLesson b = buildLesson(null, 2L, 1L, "09:30", "10:30");

        assertThatThrownBy(() -> scheduleService.batchCreate(List.of(a, b)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("教室");
    }

    @Test
    @DisplayName("批量排课批次内班级冲突 — 同一班级同一时段不同教师")
    void batchCreate_ClassConflict() {
        ScheduleLesson a = buildLesson(1L, 1L, 1L, "09:00", "10:00");
        ScheduleLesson b = buildLesson(1L, 2L, 2L, "09:30", "10:30");

        assertThatThrownBy(() -> scheduleService.batchCreate(List.of(a, b)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级");
    }

    @Test
    @DisplayName("批量排课空列表 — 不调用任何 mapper")
    void batchCreate_EmptyList() {
        scheduleService.batchCreate(Collections.emptyList());

        verify(scheduleConflictService, never()).checkConflict(any());
        verify(scheduleLessonMapper, never()).insert(any(ScheduleLesson.class));
    }

    @Test
    @DisplayName("批量排课单个元素 — 不走内层循环")
    void batchCreate_SingleElement() {
        ScheduleLesson a = buildLesson(1L, 1L, 1L, "09:00", "10:00");
        when(scheduleConflictService.checkConflict(any(ScheduleLesson.class))).thenReturn(Collections.emptyList());
        when(scheduleLessonMapper.insert(any(ScheduleLesson.class))).thenReturn(1);

        scheduleService.batchCreate(List.of(a));

        verify(scheduleLessonMapper).insert(any(ScheduleLesson.class));
    }

    private Classroom activeClassroom() {
        Classroom c = new Classroom();
        c.setStatus(1); // r20: validateClassroomExists 要求教室为启用状态
        return c;
    }
}
