package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentServiceImpl;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("报名管理单元测试")
class EnrollmentServiceMockTest {

    @Mock private EnrollmentMapper enrollmentMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private StudentMapper studentMapper;
    @Mock private UserMapper userMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;
    @Mock private RefundRecordMapper refundRecordMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
        TableInfoHelper.initTableInfo(assistant, PaymentRecord.class);
        TableInfoHelper.initTableInfo(assistant, RefundRecord.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ========== create() ==========

    @Test
    @DisplayName("create - 正常创建报名成功")
    void create_success() {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);
        // P1-1: create 需校验学员/课程存在
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());

        Enrollment result = enrollmentService.create(enrollment);

        assertThat(result).isNotNull();
        assertThat(result.getStudentId()).isEqualTo(10L);
        assertThat(result.getCourseId()).isEqualTo(20L);
        verify(enrollmentMapper).insert(enrollment);
    }

    @Test
    @DisplayName("create - 学员已报名同课程（status=2或3）应拒绝")
    void create_duplicateEnrollment_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);

        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // P1-1: create 需校验学员/课程存在
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该学员已有此课程的报名记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("create - studentId 为空应抛异常")
    void create_nullStudentId_shouldThrow() {
        Enrollment enrollment = new Enrollment();
        enrollment.setCourseId(20L);

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    // ========== 时间冲突检测 ==========

    @Test
    @DisplayName("时间冲突-TC1: 完全相同时间段应抛 409")
    void create_timeConflict_exactOverlap_shouldReject() {
        // 学员已报名班级 1（周一 09:00-10:00）
        Enrollment activeEnrollment = new Enrollment();
        activeEnrollment.setClassId(1L);
        activeEnrollment.setStudentId(10L);
        activeEnrollment.setStatus(3);

        LocalDate monday = LocalDate.of(2026, 7, 20); // 周一
        ScheduleLesson existing = buildScheduleLesson(1L, monday, "09:00", "10:00");
        ScheduleLesson target = buildScheduleLesson(2L, monday, "09:00", "10:00"); // 完全相同

        when(enrollmentMapper.selectList(argThat(q -> true))).thenReturn(
                List.of(activeEnrollment), // 第一次：活跃报名
                List.of(activeEnrollment)  // 第二次：detectTimeConflict 内部
        );
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing, target));
        when(classGroupMapper.selectClassNamesByIdsIncludeDeleted(any()))
                .thenReturn(List.of(Map.of("id", 1L, "class_name", "硬笔书法A班")));
        when(classGroupMapper.selectById(2L)).thenReturn(buildClassGroup(2L, 20L));
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(2L);

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上课时间冲突")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("时间冲突-TC2: 部分重叠应抛 409（09:00-10:00 vs 09:30-10:30）")
    void create_timeConflict_partialOverlap_shouldReject() {
        Enrollment activeEnrollment = new Enrollment();
        activeEnrollment.setClassId(1L);
        activeEnrollment.setStudentId(10L);
        activeEnrollment.setStatus(3);

        LocalDate monday = LocalDate.of(2026, 7, 20);
        ScheduleLesson existing = buildScheduleLesson(1L, monday, "09:00", "10:00");
        ScheduleLesson target = buildScheduleLesson(2L, monday, "09:30", "10:30");

        when(enrollmentMapper.selectList(argThat(q -> true))).thenReturn(
                List.of(activeEnrollment), List.of(activeEnrollment));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing, target));
        when(classGroupMapper.selectClassNamesByIdsIncludeDeleted(any()))
                .thenReturn(List.of(Map.of("id", 1L, "class_name", "硬笔书法A班")));
        when(classGroupMapper.selectById(2L)).thenReturn(buildClassGroup(2L, 20L));
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(2L);

        assertThatThrownBy(() -> enrollmentService.create(enrollment))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("上课时间冲突")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("时间冲突-TC3: 相邻不重叠（09:00-10:00 vs 10:00-11:00）应通过")
    void create_timeConflict_adjacentNoOverlap_shouldPass() {
        Enrollment activeEnrollment = new Enrollment();
        activeEnrollment.setClassId(1L);
        activeEnrollment.setStudentId(10L);
        activeEnrollment.setStatus(3);

        LocalDate monday = LocalDate.of(2026, 7, 20);
        ScheduleLesson existing = buildScheduleLesson(1L, monday, "09:00", "10:00");
        ScheduleLesson target = buildScheduleLesson(2L, monday, "10:00", "11:00");

        when(enrollmentMapper.selectList(argThat(q -> true))).thenReturn(
                List.of(activeEnrollment), List.of(activeEnrollment));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing, target));
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());
        when(classGroupMapper.selectById(2L)).thenReturn(buildClassGroup(2L, 20L));
        // 重复报名检查返回 0
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(2L);

        Enrollment result = enrollmentService.create(enrollment);
        assertThat(result).isNotNull();
        verify(enrollmentMapper).insert(enrollment);
    }

    @Test
    @DisplayName("时间冲突-TC4: 不同日期无重叠应通过")
    void create_timeConflict_differentDate_shouldPass() {
        Enrollment activeEnrollment = new Enrollment();
        activeEnrollment.setClassId(1L);
        activeEnrollment.setStudentId(10L);
        activeEnrollment.setStatus(3);

        LocalDate monday = LocalDate.of(2026, 7, 20);
        LocalDate tuesday = LocalDate.of(2026, 7, 21);
        ScheduleLesson existing = buildScheduleLesson(1L, monday, "09:00", "10:00");
        ScheduleLesson target = buildScheduleLesson(2L, tuesday, "09:00", "10:00");

        when(enrollmentMapper.selectList(argThat(q -> true))).thenReturn(
                List.of(activeEnrollment), List.of(activeEnrollment));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(existing, target));
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());
        when(classGroupMapper.selectById(2L)).thenReturn(buildClassGroup(2L, 20L));
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(2L);

        Enrollment result = enrollmentService.create(enrollment);
        assertThat(result).isNotNull();
        verify(enrollmentMapper).insert(enrollment);
    }

    @Test
    @DisplayName("时间冲突-TC5: classId=null 应跳过冲突检测")
    void create_timeConflict_nullClassId_shouldSkip() {
        when(studentMapper.selectById(10L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(courseMapper.selectById(20L)).thenReturn(activeCourse());
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.insert(any(Enrollment.class))).thenReturn(1);

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(null); // 自由排课

        Enrollment result = enrollmentService.create(enrollment);
        assertThat(result).isNotNull();
        verify(enrollmentMapper).insert(enrollment);
        // scheduleLessonMapper 不应被调用
        verify(scheduleLessonMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    // ========== audit() ==========

    @Test
    @DisplayName("audit - 正常审核通过（status=2），应设置 holdExpireTime")
    void audit_approve_shouldSetHoldExpireTime() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(1L);
        enrollment.setStatus(1);
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);

        when(enrollmentMapper.selectById(1L)).thenReturn(enrollment);
        // CAS 更新：update(null, wrapper)
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(nameResolver.getStudentName(anyLong())).thenReturn("测试学员");
        when(nameResolver.getCourseName(anyLong())).thenReturn("测试课程");

        Enrollment result = enrollmentService.audit(1L, 2, 1L, "审核通过");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getAuditorId()).isEqualTo(1L);
        assertThat(result.getAuditRemark()).isEqualTo("审核通过");
        assertThat(result.getHoldExpireTime()).isNotNull();
        verify(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("audit - 审核驳回（status=4）")
    void audit_reject_shouldSetStatus4() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(2L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectById(2L)).thenReturn(enrollment);
        // CAS 更新：update(null, wrapper)
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        Enrollment result = enrollmentService.audit(2L, 4, 1L, "资料不全");

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo(4);
        assertThat(result.getAuditorId()).isEqualTo(1L);
        assertThat(result.getAuditRemark()).isEqualTo("资料不全");
        assertThat(result.getHoldExpireTime()).isNull();
        verify(enrollmentMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("audit - 非待审核状态的报名不可审核")
    void audit_notPending_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(3L);
        enrollment.setStatus(2); // 已通过，非待审核

        when(enrollmentMapper.selectById(3L)).thenReturn(enrollment);

        assertThatThrownBy(() -> enrollmentService.audit(3L, 2, 1L, "再次审核"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("非待审核状态")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("audit - 无效审核状态（非2非4）应拒绝")
    void audit_invalidStatus_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(4L);
        enrollment.setStatus(1);

        when(enrollmentMapper.selectById(4L)).thenReturn(enrollment);

        assertThatThrownBy(() -> enrollmentService.audit(4L, 3, 1L, "无效状态"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无效的审核状态")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(enrollmentMapper, never()).update(any(), any(LambdaUpdateWrapper.class));
    }

    // ========== delete() ==========

    @Test
    @DisplayName("delete - 正常删除成功")
    void delete_success() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(5L);
        enrollment.setStudentId(10L);
        enrollment.setCourseId(20L);
        enrollment.setClassId(30L);

        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectById(5L)).thenReturn(enrollment);
        when(enrollmentMapper.deleteById(5L)).thenReturn(1);
        when(classStudentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        lenient().when(nameResolver.getStudentName(anyLong())).thenReturn("学员A");
        lenient().when(nameResolver.getCourseName(anyLong())).thenReturn("课程A");

        boolean result = enrollmentService.delete(5L);

        assertThat(result).isTrue();
        verify(enrollmentMapper).deleteById(5L);
        verify(classStudentMapper).update(any(), any(LambdaUpdateWrapper.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("delete - 有缴费记录时拒绝删除")
    void delete_hasPaymentRecords_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(6L);
        enrollment.setStatus(1); // 待审核，允许进入后续检查
        when(enrollmentMapper.selectById(6L)).thenReturn(enrollment);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.delete(6L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缴费记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("delete - 有退费记录时拒绝删除")
    void delete_hasRefundRecords_shouldReject() {
        Enrollment enrollment = new Enrollment();
        enrollment.setId(7L);
        enrollment.setStatus(1); // 待审核，允许进入后续检查
        when(enrollmentMapper.selectById(7L)).thenReturn(enrollment);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> enrollmentService.delete(7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("退费记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).deleteById(anyLong());
    }

    // ========== expirePendingEnrollments() ==========

    @Test
    @DisplayName("expirePendingEnrollments - 定时任务正常执行，过期报名标记为已失效")
    void expirePendingEnrollments_shouldUpdateExpiredRecords() {
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(3);

        enrollmentService.expirePendingEnrollments();

        verify(enrollmentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("expirePendingEnrollments - 无过期记录时不打印日志")
    void expirePendingEnrollments_noExpiredRecords() {
        when(enrollmentMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(0);

        enrollmentService.expirePendingEnrollments();

        verify(enrollmentMapper).update(isNull(), any(LambdaUpdateWrapper.class));
    }

    // ========== 测试辅助方法 ==========

    private ScheduleLesson buildScheduleLesson(Long classId, LocalDate date, String startTime, String endTime) {
        ScheduleLesson sl = new ScheduleLesson();
        sl.setClassId(classId);
        sl.setLessonDate(date);
        sl.setStartTime(LocalTime.parse(startTime));
        sl.setEndTime(LocalTime.parse(endTime));
        sl.setStatus(1);
        return sl;
    }

    private ClassGroup buildClassGroup(Long classId, Long courseId) {
        ClassGroup cg = new ClassGroup();
        cg.setId(classId);
        cg.setCourseId(courseId);
        cg.setClassName("测试班级");
        cg.setStatus(1); // r20: create() 校验班级须为开放状态
        return cg;
    }

    private com.pzhu.eduadmin.modules.course.entity.Course activeCourse() {
        com.pzhu.eduadmin.modules.course.entity.Course c = new com.pzhu.eduadmin.modules.course.entity.Course();
        c.setStatus(1); // r20: create() 校验课程须为启用状态
        return c;
    }
}
