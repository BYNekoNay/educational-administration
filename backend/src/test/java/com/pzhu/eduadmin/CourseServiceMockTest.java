package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.course.service.CourseServiceImpl;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CourseServiceMockTest {

    @Mock private CourseMapper courseMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private UserMapper userMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private PaymentRecordMapper paymentRecordMapper;

    @InjectMocks
    private CourseServiceImpl courseService;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ======================== createCourse ========================

    @Test
    @DisplayName("createCourse - 正常创建成功")
    void createCourse_success() {
        Course course = new Course();
        course.setName("钢琴基础课");
        course.setPrice(new BigDecimal("1999.00"));
        course.setTotalLessons(24);

        when(courseMapper.insert(any(Course.class))).thenReturn(1);

        Course result = courseService.createCourse(course);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("钢琴基础课");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("1999.00"));
        assertThat(result.getTotalLessons()).isEqualTo(24);
        verify(courseMapper).insert(course);
    }

    @Test
    @DisplayName("createCourse - 名称为空应拒绝")
    void createCourse_blankName_rejected() {
        Course course = new Course();
        course.setName("");
        course.setPrice(new BigDecimal("100"));
        course.setTotalLessons(10);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程名称不能为空");
    }

    @Test
    @DisplayName("createCourse - 名称为null应拒绝")
    void createCourse_nullName_rejected() {
        Course course = new Course();
        course.setName(null);
        course.setPrice(new BigDecimal("100"));
        course.setTotalLessons(10);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程名称不能为空");
    }

    @Test
    @DisplayName("createCourse - 价格为0应拒绝")
    void createCourse_zeroPrice_rejected() {
        Course course = new Course();
        course.setName("钢琴课");
        course.setPrice(BigDecimal.ZERO);
        course.setTotalLessons(10);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程价格必须大于0");
    }

    @Test
    @DisplayName("createCourse - 价格为负数应拒绝")
    void createCourse_negativePrice_rejected() {
        Course course = new Course();
        course.setName("钢琴课");
        course.setPrice(new BigDecimal("-100"));
        course.setTotalLessons(10);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程价格必须大于0");
    }

    @Test
    @DisplayName("createCourse - 价格为null应拒绝")
    void createCourse_nullPrice_rejected() {
        Course course = new Course();
        course.setName("钢琴课");
        course.setPrice(null);
        course.setTotalLessons(10);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程价格必须大于0");
    }

    @Test
    @DisplayName("createCourse - 课时数为0应拒绝")
    void createCourse_zeroTotalLessons_rejected() {
        Course course = new Course();
        course.setName("钢琴课");
        course.setPrice(new BigDecimal("100"));
        course.setTotalLessons(0);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课时总数必须大于0");
    }

    @Test
    @DisplayName("createCourse - 课时数为负数应拒绝")
    void createCourse_negativeTotalLessons_rejected() {
        Course course = new Course();
        course.setName("钢琴课");
        course.setPrice(new BigDecimal("100"));
        course.setTotalLessons(-5);

        assertThatThrownBy(() -> courseService.createCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课时总数必须大于0");
    }

    // ======================== updateCourse ========================

    @Test
    @DisplayName("updateCourse - 正常更新成功")
    void updateCourse_success() {
        Course existing = new Course();
        existing.setId(1L);
        existing.setName("旧名称");
        existing.setPrice(new BigDecimal("1000"));
        existing.setTotalLessons(20);

        Course updateInput = new Course();
        updateInput.setId(1L);
        updateInput.setName("新课程");

        Course updatedResult = new Course();
        updatedResult.setId(1L);
        updatedResult.setName("新课程");
        updatedResult.setPrice(new BigDecimal("1000"));
        updatedResult.setTotalLessons(20);

        when(courseMapper.selectById(1L)).thenReturn(existing, updatedResult);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        Course result = courseService.updateCourse(updateInput);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("新课程");
        verify(courseMapper).updateById(any(Course.class));
    }

    @Test
    @DisplayName("updateCourse - 课程不存在应拒绝")
    void updateCourse_notFound_rejected() {
        Course course = new Course();
        course.setId(999L);

        when(courseMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> courseService.updateCourse(course))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程不存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    @DisplayName("updateCourse - 有缴费记录时修改价格应拒绝")
    void updateCourse_withPayments_changePrice_rejected() {
        Course existing = new Course();
        existing.setId(1L);
        existing.setName("钢琴课");
        existing.setPrice(new BigDecimal("1000"));
        existing.setTotalLessons(20);

        Course updateInput = new Course();
        updateInput.setId(1L);
        updateInput.setPrice(new BigDecimal("2000"));

        when(courseMapper.selectById(1L)).thenReturn(existing);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> courseService.updateCourse(updateInput))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改价格")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("updateCourse - 有缴费记录时修改课时数应拒绝")
    void updateCourse_withPayments_changeTotalLessons_rejected() {
        Course existing = new Course();
        existing.setId(1L);
        existing.setName("钢琴课");
        existing.setPrice(new BigDecimal("1000"));
        existing.setTotalLessons(20);

        Course updateInput = new Course();
        updateInput.setId(1L);
        updateInput.setTotalLessons(30);

        when(courseMapper.selectById(1L)).thenReturn(existing);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> courseService.updateCourse(updateInput))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改课时数")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));
    }

    @Test
    @DisplayName("updateCourse - 有缴费记录时仅修改名称应允许")
    void updateCourse_withPayments_changeNameOnly_allowed() {
        Course existing = new Course();
        existing.setId(1L);
        existing.setName("旧名称");
        existing.setPrice(new BigDecimal("1000"));
        existing.setTotalLessons(20);

        Course updateInput = new Course();
        updateInput.setId(1L);
        updateInput.setName("新名称");

        Course updatedResult = new Course();
        updatedResult.setId(1L);
        updatedResult.setName("新名称");
        updatedResult.setPrice(new BigDecimal("1000"));
        updatedResult.setTotalLessons(20);

        when(courseMapper.selectById(1L)).thenReturn(existing, updatedResult);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);
        when(courseMapper.updateById(any(Course.class))).thenReturn(1);

        Course result = courseService.updateCourse(updateInput);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("新名称");
        verify(courseMapper).updateById(any(Course.class));
    }

    // ======================== deleteCourse ========================

    @Test
    @DisplayName("deleteCourse - 正常删除成功")
    void deleteCourse_success() {
        Course course = new Course();
        course.setId(1L);
        course.setName("测试课程");
        when(courseMapper.selectById(1L)).thenReturn(course);
        when(classGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(classGroupMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(courseMapper.deleteById(1L)).thenReturn(1);

        boolean result = courseService.deleteCourse(1L);

        assertThat(result).isTrue();
        verify(courseMapper).deleteById(1L);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("deleteCourse - 有活跃班级时拒绝删除")
    void deleteCourse_activeClasses_rejected() {
        when(classGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> courseService.deleteCourse(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("活跃班级")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(courseMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteCourse - 有未来排课时拒绝删除")
    void deleteCourse_futureLessons_rejected() {
        ClassGroup cg = new ClassGroup();
        cg.setId(10L);

        when(classGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(classGroupMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cg));
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> courseService.deleteCourse(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未来的排课记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(courseMapper, never()).deleteById(anyLong());
    }

    // ======================== deleteCourse — not found ========================

    @Test
    @DisplayName("deleteCourse - 课程不存在应抛出404")
    void deleteCourse_notFound_throws404() {
        when(classGroupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(classGroupMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(courseMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> courseService.deleteCourse(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课程不存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verify(courseMapper, never()).deleteById(anyLong());
    }

    // ======================== deleteClassGroup ========================

    @Test
    @DisplayName("deleteClassGroup - 正常删除成功")
    void deleteClassGroup_success() {
        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(10L);
        classGroup.setClassName("测试班级");
        when(classGroupMapper.selectById(10L)).thenReturn(classGroup);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(classGroupMapper.deleteById(10L)).thenReturn(1);

        boolean result = courseService.deleteClassGroup(10L);

        assertThat(result).isTrue();
        verify(classGroupMapper).deleteById(10L);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("deleteClassGroup - 有学员时拒绝删除")
    void deleteClassGroup_withStudents_rejected() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        assertThatThrownBy(() -> courseService.deleteClassGroup(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在学员")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(classGroupMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteClassGroup - 有未来排课时拒绝删除")
    void deleteClassGroup_futureLessons_rejected() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> courseService.deleteClassGroup(10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("排课记录")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(classGroupMapper, never()).deleteById(anyLong());
    }

    @Test
    @DisplayName("deleteClassGroup - 班级不存在应抛出404")
    void deleteClassGroup_notFound_throws404() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(scheduleLessonMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(classGroupMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> courseService.deleteClassGroup(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verify(classGroupMapper, never()).deleteById(anyLong());
    }

    // ======================== removeStudentFromClass ========================

    @Test
    @DisplayName("removeStudentFromClass - 正常移除成功且日志在删除后记录")
    void removeStudentFromClass_success_logsAfterDelete() {
        ClassStudent cs = new ClassStudent();
        cs.setId(1L);
        cs.setClassId(10L);
        cs.setStudentId(100L);
        cs.setStatus(1);

        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cs));
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        when(nameResolver.getClassName(10L)).thenReturn("测试班级");
        when(nameResolver.getStudentName(100L)).thenReturn("测试学员");

        boolean result = courseService.removeStudentFromClass(10L, 100L);

        assertThat(result).isTrue();
        // Critical fix 后改为置 status=3 并 updateById（保留记录用于流失统计）
        assertThat(cs.getStatus()).isEqualTo(3);
        verify(classStudentMapper).updateById(cs);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("removeStudentFromClass - 学员不在班级中应抛出404且不记录日志")
    void removeStudentFromClass_notFound_noLog() {
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> courseService.removeStudentFromClass(10L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该学员不在此班级中");

        verify(classStudentMapper, never()).deleteById(anyLong());
        verify(operationLogService, never()).log(anyString(), anyString());
    }

    // ======================== addStudentToClass ========================

    @Test
    @DisplayName("addStudentToClass - 正常添加成功")
    void addStudentToClass_success() {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(10L);
        cs.setStudentId(100L);

        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(10L);
        classGroup.setMaxStudentCount(20);

        when(classGroupMapper.selectById(10L)).thenReturn(classGroup);
        when(studentMapper.selectById(100L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)  // 重复检查：不存在
                .thenReturn(5L); // 当前人数：5人
        when(classStudentMapper.insert(any(ClassStudent.class))).thenReturn(1);

        boolean result = courseService.addStudentToClass(cs);

        assertThat(result).isTrue();
        verify(classStudentMapper).insert(cs);
    }

    @Test
    @DisplayName("addStudentToClass - 班级已满时拒绝")
    void addStudentToClass_full_rejected() {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(10L);
        cs.setStudentId(100L);

        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(10L);
        classGroup.setMaxStudentCount(10);

        when(classGroupMapper.selectById(10L)).thenReturn(classGroup);
        when(studentMapper.selectById(100L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(0L)   // 重复检查：不存在
                .thenReturn(10L); // 当前人数：10人（已满）

        assertThatThrownBy(() -> courseService.addStudentToClass(cs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级已满")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("addStudentToClass - 学员已在班级中应拒绝")
    void addStudentToClass_alreadyExists_rejected() {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(10L);
        cs.setStudentId(100L);

        ClassGroup classGroup = new ClassGroup();
        classGroup.setId(10L);
        classGroup.setMaxStudentCount(20);

        when(classGroupMapper.selectById(10L)).thenReturn(classGroup);
        when(studentMapper.selectById(100L)).thenReturn(new com.pzhu.eduadmin.modules.student.entity.Student());
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> courseService.addStudentToClass(cs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("该学员已在此班级中")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(409));

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    @DisplayName("addStudentToClass - 班级不存在应拒绝")
    void addStudentToClass_classNotFound_rejected() {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(999L);
        cs.setStudentId(100L);

        when(classGroupMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> courseService.addStudentToClass(cs))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("班级不存在")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }
}
