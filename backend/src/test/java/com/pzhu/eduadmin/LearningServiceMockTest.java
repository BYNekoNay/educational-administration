package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.learning.entity.Homework;
import com.pzhu.eduadmin.modules.learning.entity.LearningRecord;
import com.pzhu.eduadmin.modules.learning.mapper.HomeworkMapper;
import com.pzhu.eduadmin.modules.learning.mapper.LearningRecordMapper;
import com.pzhu.eduadmin.modules.learning.service.LearningServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("学情服务 Mock 单元测试")
class LearningServiceMockTest {

    @Mock private HomeworkMapper homeworkMapper;
    @Mock private LearningRecordMapper learningRecordMapper;
    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;

    @InjectMocks
    private LearningServiceImpl learningService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Homework.class);
        TableInfoHelper.initTableInfo(asst, LearningRecord.class);
        TableInfoHelper.initTableInfo(asst, ClassStudent.class);
        TableInfoHelper.initTableInfo(asst, ScheduleLesson.class);
    }

    // ============ Homework ============

    @Test
    @DisplayName("分页查询作业")
    void pageHomeworks_Success() {
        Homework hw = new Homework();
        hw.setId(1L);
        hw.setContent("练习曲");
        Page<Homework> mp = new Page<>(1, 10);
        mp.setRecords(List.of(hw));
        when(homeworkMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<Homework> result = learningService.pageHomeworks(1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("创建作业 lessonId 为空 — 抛出 400")
    void createHomework_LessonIdNull() {
        Homework hw = new Homework();
        hw.setContent("作业内容");

        assertThatThrownBy(() -> learningService.createHomework(hw))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("课次ID不能为空");
    }

    @Test
    @DisplayName("正常创建作业 — 自动回填 ID")
    void createHomework_Success() {
        Homework hw = new Homework();
        hw.setLessonId(10L);
        hw.setContent("练习曲");
        doAnswer(inv -> { inv.getArgument(0, Homework.class).setId(100L); return 1; })
                .when(homeworkMapper).insert(any(Homework.class));

        Homework result = learningService.createHomework(hw);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("按课次查询作业列表")
    void getHomeworksByLessonId_Success() {
        Homework hw = new Homework();
        hw.setId(1L);
        hw.setLessonId(10L);
        when(homeworkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(hw));

        List<Homework> result = learningService.getHomeworksByLessonId(10L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLessonId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("按学员查询所在班级全部作业")
    void getHomeworksByStudentId_Success() {
        ClassStudent enrollment = new ClassStudent();
        enrollment.setClassId(5L);
        enrollment.setStudentId(20L);
        enrollment.setStatus(1);

        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(10L);
        lesson.setClassId(5L);

        Homework homework = new Homework();
        homework.setId(1L);
        homework.setLessonId(10L);

        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(enrollment));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lesson));
        when(homeworkMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(homework));

        List<Homework> result = learningService.getHomeworksByStudentId(20L);

        assertThat(result).containsExactly(homework);
    }

    // ============ LearningRecord ============

    @Test
    @DisplayName("分页查询学情记录")
    void pageLearningRecords_Success() {
        LearningRecord lr = new LearningRecord();
        lr.setId(1L);
        Page<LearningRecord> mp = new Page<>(1, 10);
        mp.setRecords(List.of(lr));
        when(learningRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<LearningRecord> result = learningService.pageLearningRecords(1, 10);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("创建学情记录 studentId 为空 — 抛出 400")
    void createLearningRecord_StudentIdNull() {
        LearningRecord lr = new LearningRecord();
        lr.setTeacherComment("表现优秀");

        assertThatThrownBy(() -> learningService.createLearningRecord(lr))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空");
    }

    @Test
    @DisplayName("正常创建学情记录")
    void createLearningRecord_Success() {
        LearningRecord lr = new LearningRecord();
        lr.setStudentId(1L);
        lr.setTeacherComment("表现优秀");
        doAnswer(inv -> { inv.getArgument(0, LearningRecord.class).setId(200L); return 1; })
                .when(learningRecordMapper).insert(any(LearningRecord.class));

        LearningRecord result = learningService.createLearningRecord(lr);

        assertThat(result.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("按课次和学员查询学情记录")
    void getRecordsByLessonIdAndStudentId_Success() {
        LearningRecord lr = new LearningRecord();
        lr.setId(1L);
        lr.setLessonId(10L);
        lr.setStudentId(20L);
        when(learningRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lr));

        List<LearningRecord> result = learningService.getRecordsByLessonIdAndStudentId(10L, 20L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("按课次查询学情记录")
    void getRecordsByLessonId_Success() {
        LearningRecord lr = new LearningRecord();
        lr.setId(1L);
        when(learningRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lr));

        List<LearningRecord> result = learningService.getRecordsByLessonId(10L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("按学员查询学情记录")
    void getRecordsByStudentId_Success() {
        LearningRecord lr = new LearningRecord();
        lr.setId(1L);
        when(learningRecordMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lr));

        List<LearningRecord> result = learningService.getRecordsByStudentId(20L);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("批量创建学情记录 — 正常创建含去重")
    void batchCreateRecords_Success() {
        LearningRecord r1 = new LearningRecord();
        r1.setStudentId(1L);
        r1.setLessonId(10L);
        r1.setTeacherComment("优秀");

        LearningRecord r2 = new LearningRecord();
        r2.setStudentId(2L);
        r2.setLessonId(10L);
        r2.setTeacherComment("良好");

        // r1 已存在，r2 不存在
        when(learningRecordMapper.selectCount(any(LambdaQueryWrapper.class)))
                .thenReturn(1L)   // r1 exists
                .thenReturn(0L);  // r2 doesn't exist
        doAnswer(inv -> { inv.getArgument(0, LearningRecord.class).setId(200L); return 1; })
                .when(learningRecordMapper).insert(any(LearningRecord.class));

        List<LearningRecord> result = learningService.batchCreateRecords(List.of(r1, r2));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("批量创建 studentId 为空 — 抛出 400")
    void batchCreateRecords_StudentIdNull() {
        LearningRecord r1 = new LearningRecord();
        r1.setLessonId(10L);

        assertThatThrownBy(() -> learningService.batchCreateRecords(List.of(r1)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空");
    }

    @Test
    @DisplayName("批量创建空列表 — 返回空")
    void batchCreateRecords_Empty() {
        List<LearningRecord> result = learningService.batchCreateRecords(List.of());

        assertThat(result).isEmpty();
        verify(learningRecordMapper, never()).insert(any(LearningRecord.class));
    }
}
