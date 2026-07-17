package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.exam.entity.ExamLevel;
import com.pzhu.eduadmin.modules.exam.entity.ExamSignup;
import com.pzhu.eduadmin.modules.exam.mapper.ExamLevelMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import com.pzhu.eduadmin.modules.exam.service.ExamServiceImpl;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("考级服务 Mock 单元测试")
class ExamServiceMockTest {

    @Mock private ExamLevelMapper examLevelMapper;
    @Mock private ExamSignupMapper examSignupMapper;
    @Mock private StudentMapper studentMapper;

    @InjectMocks
    private ExamServiceImpl examService;

    private MockedStatic<QueryHelper> queryHelperMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, ExamLevel.class);
        TableInfoHelper.initTableInfo(asst, ExamSignup.class);
        TableInfoHelper.initTableInfo(asst, Student.class);
    }

    @BeforeEach
    void setUp() {
        queryHelperMock = mockStatic(QueryHelper.class);
    }

    @AfterEach
    void tearDown() {
        if (queryHelperMock != null) queryHelperMock.close();
    }

    // ============ ExamLevel ============

    @Test
    @DisplayName("分页查询考级项目")
    void pageExamLevels_Success() {
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        el.setName("钢琴考级");
        Page<ExamLevel> mp = new Page<>(1, 10);
        mp.setRecords(List.of(el));
        when(examLevelMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<ExamLevel> result = examService.pageExamLevels(1, 10, null, null, null);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getName()).isEqualTo("钢琴考级");
    }

    @Test
    @DisplayName("按 ID 查询考级项目")
    void getExamLevelById_Found() {
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        when(examLevelMapper.selectById(1L)).thenReturn(el);

        ExamLevel result = examService.getExamLevelById(1L);

        assertThat(result.getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("创建考级项目名称不能为空")
    void createExamLevel_NameBlank() {
        ExamLevel el = new ExamLevel();
        el.setName("");

        assertThatThrownBy(() -> examService.createExamLevel(el))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考级项目名称不能为空");
    }

    @Test
    @DisplayName("创建考级项目名称不能为 null")
    void createExamLevel_NameNull() {
        ExamLevel el = new ExamLevel();

        assertThatThrownBy(() -> examService.createExamLevel(el))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考级项目名称不能为空");
    }

    @Test
    @DisplayName("正常创建考级项目 — 自动回填 ID")
    void createExamLevel_Success() {
        ExamLevel el = new ExamLevel();
        el.setName("钢琴考级");
        el.setFee(new BigDecimal("200"));
        doAnswer(inv -> { inv.getArgument(0, ExamLevel.class).setId(100L); return 1; })
                .when(examLevelMapper).insert(any(ExamLevel.class));

        ExamLevel result = examService.createExamLevel(el);

        assertThat(result.getId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("更新考级项目 — 返回更新后数据")
    void updateExamLevel_Success() {
        when(examLevelMapper.updateById(any(ExamLevel.class))).thenReturn(1);
        ExamLevel updated = new ExamLevel();
        updated.setId(50L);
        updated.setName("更新后");
        when(examLevelMapper.selectById(50L)).thenReturn(updated);

        ExamLevel el = new ExamLevel();
        el.setId(50L);
        el.setName("新名称");

        ExamLevel result = examService.updateExamLevel(el);

        assertThat(result.getName()).isEqualTo("更新后");
    }

    @Test
    @DisplayName("删除考级项目有报名记录 — 抛出 409")
    void deleteExamLevel_HasSignups() {
        when(examSignupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        assertThatThrownBy(() -> examService.deleteExamLevel(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有报名记录");
    }

    @Test
    @DisplayName("删除考级项目无报名记录 — 正常删除")
    void deleteExamLevel_Success() {
        when(examSignupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(examLevelMapper.deleteById(1L)).thenReturn(1);

        examService.deleteExamLevel(1L);

        verify(examLevelMapper).deleteById(1L);
    }

    // ============ ExamSignup ============

    @Test
    @DisplayName("分页查询报名列表含名称填充")
    void pageExamSignups_Success() {
        ExamSignup signup = new ExamSignup();
        signup.setId(1L);
        signup.setExamId(10L);
        signup.setStudentId(20L);
        Page<ExamSignup> mp = new Page<>(1, 10);
        mp.setRecords(List.of(signup));
        when(examSignupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        ExamLevel level = new ExamLevel();
        level.setId(10L);
        level.setName("钢琴");
        level.setLevelName("三级");
        when(examLevelMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(level));

        Student stu = new Student();
        stu.setId(20L);
        stu.setName("张三");
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(stu));

        Page<ExamSignup> result = examService.pageExamSignups(1, 10, null, null);

        assertThat(result.getRecords().get(0).getExamName()).contains("钢琴");
        assertThat(result.getRecords().get(0).getStudentName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("创建报名 studentId 为空 — 抛出 400")
    void createExamSignup_StudentIdNull() {
        ExamSignup signup = new ExamSignup();
        signup.setExamId(1L);

        assertThatThrownBy(() -> examService.createExamSignup(signup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员ID不能为空");
    }

    @Test
    @DisplayName("创建报名 examId 为空 — 抛出 400")
    void createExamSignup_ExamIdNull() {
        ExamSignup signup = new ExamSignup();
        signup.setStudentId(1L);

        assertThatThrownBy(() -> examService.createExamSignup(signup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考级项目ID不能为空");
    }

    @Test
    @DisplayName("创建报名考级项目不存在 — 抛出 404")
    void createExamSignup_ExamNotFound() {
        ExamSignup signup = new ExamSignup();
        signup.setStudentId(1L);
        signup.setExamId(999L);
        when(examLevelMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> examService.createExamSignup(signup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("考级项目不存在");
    }

    @Test
    @DisplayName("创建报名学员不存在 — 抛出 404")
    void createExamSignup_StudentNotFound() {
        ExamSignup signup = new ExamSignup();
        signup.setStudentId(999L);
        signup.setExamId(1L);
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        when(examLevelMapper.selectById(1L)).thenReturn(el);
        when(studentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> examService.createExamSignup(signup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员不存在");
    }

    @Test
    @DisplayName("重复报名 — 抛出 409")
    void createExamSignup_Duplicate() {
        ExamSignup signup = new ExamSignup();
        signup.setStudentId(1L);
        signup.setExamId(1L);
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        when(examLevelMapper.selectById(1L)).thenReturn(el);
        Student stu = new Student();
        stu.setId(1L);
        when(studentMapper.selectById(1L)).thenReturn(stu);
        when(examSignupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> examService.createExamSignup(signup))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已报名");
    }

    @Test
    @DisplayName("正常创建报名")
    void createExamSignup_Success() {
        ExamSignup signup = new ExamSignup();
        signup.setStudentId(1L);
        signup.setExamId(1L);
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        when(examLevelMapper.selectById(1L)).thenReturn(el);
        Student stu = new Student();
        stu.setId(1L);
        when(studentMapper.selectById(1L)).thenReturn(stu);
        when(examSignupMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> { inv.getArgument(0, ExamSignup.class).setId(200L); return 1; })
                .when(examSignupMapper).insert(any(ExamSignup.class));

        ExamSignup result = examService.createExamSignup(signup);

        assertThat(result.getId()).isEqualTo(200L);
    }

    @Test
    @DisplayName("更新报名信息")
    void updateExamSignup_Success() {
        when(examSignupMapper.updateById(any(ExamSignup.class))).thenReturn(1);
        ExamSignup updated = new ExamSignup();
        updated.setId(10L);
        updated.setScore(new BigDecimal("85"));
        when(examSignupMapper.selectById(10L)).thenReturn(updated);

        ExamSignup signup = new ExamSignup();
        signup.setId(10L);
        signup.setScore(new BigDecimal("85"));

        ExamSignup result = examService.updateExamSignup(signup);

        assertThat(result.getScore()).isEqualByComparingTo("85");
    }

    @Test
    @DisplayName("分页查询考级 — 按关键词搜索")
    void pageExamLevels_WithKeyword() {
        ExamLevel el = new ExamLevel();
        el.setId(1L);
        el.setName("钢琴");
        Page<ExamLevel> mp = new Page<>(1, 10);
        mp.setRecords(List.of(el));
        when(examLevelMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<ExamLevel> result = examService.pageExamLevels(1, 10, "钢琴", null, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("分页查询考级 — 按指定字段排序")
    void pageExamLevels_WithSort() {
        when(examLevelMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<>(1, 10));

        // No exception means sort mapping is valid
        examService.pageExamLevels(1, 10, null, "name", "asc");
    }
}
