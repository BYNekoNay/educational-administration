package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentClassVO;
import com.pzhu.eduadmin.modules.enrollment.dto.ParentEnrollmentSnapshotVO;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentServiceImpl;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("家长报名决策快照服务测试")
class ParentEnrollmentSnapshotServiceTest {

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
    @Mock private ParentStudentMapper parentStudentMapper;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, Enrollment.class);
        TableInfoHelper.initTableInfo(assistant, ParentStudent.class);
        TableInfoHelper.initTableInfo(assistant, ClassStudent.class);
        TableInfoHelper.initTableInfo(assistant, ScheduleLesson.class);
    }

    @Test
    @DisplayName("快照一次返回课程、开班、已报名状态、冲突和服务端版本")
    void getParentEnrollmentSnapshot_happyPath() {
        EnrollmentServiceImpl service = spy(enrollmentService);
        Course enrolledCourse = course(10L, "钢琴");
        Course availableCourse = course(20L, "美术");
        ParentClassVO enrolledClass = parentClass(101L, 12, 5);
        ParentClassVO conflictClass = parentClass(201L, 0, 3);
        Enrollment activeEnrollment = new Enrollment();
        activeEnrollment.setCourseId(10L);
        activeEnrollment.setStatus(2);
        activeEnrollment.setHoldExpireTime(LocalDateTime.of(2026, 8, 6, 20, 0));

        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(enrolledCourse, availableCourse));
        when(enrollmentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(activeEnrollment));
        doReturn(List.of(enrolledClass)).when(service).listParentCourseClasses(10L);
        doReturn(List.of(conflictClass)).when(service).listParentCourseClasses(20L);
        doReturn(null).when(service).detectTimeConflict(99L, 101L);
        doReturn(Map.of(
                "conflictClassName", "周末钢琴班",
                "conflictDetail", "周六 09:00-10:00 与 09:30-10:30 重叠"))
                .when(service).detectTimeConflict(99L, 201L);

        ParentEnrollmentSnapshotVO snapshot = service.getParentEnrollmentSnapshot(7L, 99L);

        assertThat(snapshot.getStudentId()).isEqualTo(99L);
        assertThat(snapshot.getSnapshotAt()).isNotNull();
        assertThat(snapshot.getSnapshotExpiresAt()).isAfter(snapshot.getSnapshotAt());
        assertThat(snapshot.getVersionToken()).startsWith("v1-");
        assertThat(snapshot.getCourses()).hasSize(2);
        assertThat(snapshot.getCourses().get(0).isEnrolled()).isTrue();
        assertThat(snapshot.getCourses().get(0).getActiveEnrollmentStatus()).isEqualTo(2);
        assertThat(snapshot.getCourses().get(0).getHoldExpireTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 6, 20, 0));
        assertThat(snapshot.getCourses().get(0).getAvailableClassCount()).isEqualTo(1);
        assertThat(snapshot.getCourses().get(1).isEnrolled()).isFalse();
        assertThat(snapshot.getCourses().get(1).getConflicts())
                .singleElement()
                .satisfies(conflict -> {
                    assertThat(conflict.getClassId()).isEqualTo(201L);
                    assertThat(conflict.getConflictClassName()).isEqualTo("周末钢琴班");
                    assertThat(conflict.getDescription()).contains("周六 09:00-10:00");
                });
    }

    @Test
    @DisplayName("未绑定的学员不能读取快照")
    void getParentEnrollmentSnapshot_unauthorizedStudent() {
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> enrollmentService.getParentEnrollmentSnapshot(7L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(403));

        verifyNoInteractions(courseMapper);
    }

    @Test
    @DisplayName("未选择学员时不能读取快照")
    void getParentEnrollmentSnapshot_missingStudent() {
        assertThatThrownBy(() -> enrollmentService.getParentEnrollmentSnapshot(7L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(parentStudentMapper, courseMapper);
    }

    @Test
    @DisplayName("没有可用课程时返回带版本的空快照")
    void getParentEnrollmentSnapshot_emptyCourses() {
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        ParentEnrollmentSnapshotVO snapshot = enrollmentService.getParentEnrollmentSnapshot(7L, 99L);

        assertThat(snapshot.getCourses()).isEmpty();
        assertThat(snapshot.getSnapshotAt()).isNotNull();
        assertThat(snapshot.getVersionToken()).isNotBlank();
        verifyNoInteractions(enrollmentMapper);
    }

    @Test
    @DisplayName("不存在或已过期的快照版本不能提交报名")
    void createParentEnrollmentFromSnapshot_expiredVersionRejected() {
        Enrollment enrollment = enrollment(99L, 10L, 101L);

        assertThatThrownBy(() -> enrollmentService.createParentEnrollmentFromSnapshot(
                7L, enrollment, "v1-expired"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失效或过期")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("快照超过五分钟有效期后拒绝提交")
    void createParentEnrollmentFromSnapshot_expiredByServerClockRejected() {
        Clock issuedAt = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);
        ReflectionTestUtils.setField(enrollmentService, "parentSnapshotClock", issuedAt);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        ParentEnrollmentSnapshotVO snapshot = enrollmentService.getParentEnrollmentSnapshot(7L, 99L);

        ReflectionTestUtils.setField(enrollmentService, "parentSnapshotClock",
                Clock.offset(issuedAt, java.time.Duration.ofMinutes(6)));

        assertThatThrownBy(() -> enrollmentService.createParentEnrollmentFromSnapshot(
                7L, enrollment(99L, 10L, 101L), snapshot.getVersionToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("失效或过期")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("读取快照后容量发生变化时拒绝提交")
    void createParentEnrollmentFromSnapshot_changedCapacityRejected() {
        EnrollmentServiceImpl service = spy(enrollmentService);
        Course course = course(10L, "钢琴");
        ParentClassVO initialClass = parentClass(101L, 12, 5);
        ParentClassVO nowFullClass = parentClass(101L, 12, 12);

        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(course));
        when(enrollmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        doReturn(List.of(initialClass), List.of(nowFullClass))
                .when(service).listParentCourseClasses(10L);
        doReturn(null).when(service).detectTimeConflict(99L, 101L);

        ParentEnrollmentSnapshotVO snapshot = service.getParentEnrollmentSnapshot(7L, 99L);

        assertThatThrownBy(() -> service.createParentEnrollmentFromSnapshot(
                7L, enrollment(99L, 10L, 101L), snapshot.getVersionToken()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("状态已变化")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(enrollmentMapper, never()).insert(any(Enrollment.class));
    }

    @Test
    @DisplayName("版本仍有效且决策状态未变化时提交报名")
    void createParentEnrollmentFromSnapshot_validVersionCreatesEnrollment() {
        EnrollmentServiceImpl service = spy(enrollmentService);
        Course course = course(10L, "钢琴");
        ParentClassVO availableClass = parentClass(101L, 12, 5);
        Enrollment request = enrollment(99L, 10L, 101L);

        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(courseMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(course));
        when(enrollmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());
        doReturn(List.of(availableClass)).when(service).listParentCourseClasses(10L);
        doReturn(null).when(service).detectTimeConflict(99L, 101L);
        doReturn(request).when(service).create(request);

        ParentEnrollmentSnapshotVO snapshot = service.getParentEnrollmentSnapshot(7L, 99L);

        Enrollment created = service.createParentEnrollmentFromSnapshot(
                7L, request, snapshot.getVersionToken());

        assertThat(created).isSameAs(request);
        verify(service).create(request);
    }

    @Test
    @DisplayName("开班容量同时保留有限名额与不限名额语义")
    void listParentCourseClasses_mapsLimitedAndUnlimitedCapacity() {
        ClassGroup limited = classGroup(101L, 12);
        ClassGroup unlimited = classGroup(102L, null);
        ClassStudent limitedStudent = classStudent(101L);
        ClassStudent unlimitedStudent1 = classStudent(102L);
        ClassStudent unlimitedStudent2 = classStudent(102L);

        when(courseMapper.selectClassGroupsByCourseId(10L)).thenReturn(List.of(limited, unlimited));
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(limitedStudent, unlimitedStudent1, unlimitedStudent2));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of());

        List<ParentClassVO> classes = enrollmentService.listParentCourseClasses(10L);

        assertThat(classes).extracting(ParentClassVO::getMaxStudentCount)
                .containsExactly(12, 0);
        assertThat(classes).extracting(ParentClassVO::getCurrentStudentCount)
                .containsExactly(1, 2);
    }

    private Course course(Long id, String name) {
        Course course = new Course();
        course.setId(id);
        course.setName(name);
        course.setStatus(1);
        return course;
    }

    private ParentClassVO parentClass(Long id, int max, int current) {
        ParentClassVO parentClass = new ParentClassVO();
        parentClass.setId(id);
        parentClass.setClassName("班级" + id);
        parentClass.setMaxStudentCount(max);
        parentClass.setCurrentStudentCount(current);
        parentClass.setStatus(1);
        return parentClass;
    }

    private ClassGroup classGroup(Long id, Integer maxStudentCount) {
        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(id);
        classGroup.setCourseId(10L);
        classGroup.setClassName("班级" + id);
        classGroup.setMaxStudentCount(maxStudentCount);
        classGroup.setStartDate(LocalDate.of(2026, 9, 1));
        classGroup.setStatus(1);
        return classGroup;
    }

    private ClassStudent classStudent(Long classId) {
        ClassStudent classStudent = new ClassStudent();
        classStudent.setClassId(classId);
        classStudent.setStatus(1);
        return classStudent;
    }

    private Enrollment enrollment(Long studentId, Long courseId, Long classId) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setCourseId(courseId);
        enrollment.setClassId(classId);
        return enrollment;
    }
}
