package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.risk.RiskRuleProperties;
import com.pzhu.eduadmin.modules.risk.dto.RiskStudentVO;
import com.pzhu.eduadmin.modules.risk.entity.StudentRiskFollowup;
import com.pzhu.eduadmin.modules.risk.mapper.StudentRiskFollowupMapper;
import com.pzhu.eduadmin.modules.risk.service.RiskWarningServiceImpl;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流失预警服务 Mock 单元测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("流失预警服务 Mock 单元测试")
class RiskWarningServiceTest {

    @Mock private ClassStudentMapper classStudentMapper;
    @Mock private ClassGroupMapper classGroupMapper;
    @Mock private CourseMapper courseMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private LessonAccountMapper lessonAccountMapper;
    @Mock private ParentStudentMapper parentStudentMapper;
    @Mock private StudentRiskFollowupMapper followupMapper;
    @Mock private NotificationService notificationService;

    @Spy private RiskRuleProperties ruleProperties = new RiskRuleProperties();

    @InjectMocks private RiskWarningServiceImpl riskWarningService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, ClassStudent.class);
        TableInfoHelper.initTableInfo(asst, ClassGroup.class);
        TableInfoHelper.initTableInfo(asst, Student.class);
        TableInfoHelper.initTableInfo(asst, ScheduleLesson.class);
        TableInfoHelper.initTableInfo(asst, Attendance.class);
        TableInfoHelper.initTableInfo(asst, LessonAccount.class);
        TableInfoHelper.initTableInfo(asst, ParentStudent.class);
        TableInfoHelper.initTableInfo(asst, StudentRiskFollowup.class);
    }

    // ---------- 工具 ----------

    private ClassStudent member(long classId, long studentId) {
        ClassStudent cs = new ClassStudent();
        cs.setClassId(classId);
        cs.setStudentId(studentId);
        cs.setStatus(1);
        return cs;
    }

    private ClassGroup clazz(long id, long courseId, String name) {
        ClassGroup cg = new ClassGroup();
        cg.setId(id);
        cg.setCourseId(courseId);
        cg.setClassName(name);
        cg.setStatus(1);
        return cg;
    }

    private Student activeStudent(long id, String name) {
        Student s = new Student();
        s.setId(id);
        s.setStatus(1);
        s.setName(name);
        return s;
    }

    private ScheduleLesson lesson(long id, long classId, LocalDate date) {
        ScheduleLesson l = new ScheduleLesson();
        l.setId(id);
        l.setClassId(classId);
        l.setLessonDate(date);
        l.setStatus(2);
        return l;
    }

    private Attendance attendance(long lessonId, long studentId, int status) {
        Attendance a = new Attendance();
        a.setLessonId(lessonId);
        a.setStudentId(studentId);
        a.setStatus(status);
        return a;
    }

    private Map<String, Object> courseRow(long id, String name) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("name", name);
        return row;
    }

    private void stubBasicEnrollment(ClassStudent member, ClassGroup cg, Student student) {
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(classGroupMapper.selectBatchIds(anyCollection())).thenReturn(List.of(cg));
        when(studentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(student));
    }

    private void stubFollowupEmpty() {
        when(followupMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
    }

    // ---------- 用例 ----------

    @Test
    @DisplayName("示例：缺勤2/5+余量3/20+20天后到期 → 68 高风险，出现在名单首行")
    void listRiskWarnings_exampleHighRisk() {
        LocalDate today = LocalDate.now();
        ClassStudent member = member(1L, 10L);
        stubBasicEnrollment(member, clazz(1L, 100L, "钢琴A班"), activeStudent(10L, "小明"));
        when(courseMapper.selectNamesByIdsIncludeDeleted(anyCollection()))
                .thenReturn(List.of(courseRow(100L, "钢琴")));

        // 密集课次（近 20-16 天），小明到课3次后连续缺勤2次
        ScheduleLesson l0 = lesson(1L, 1L, today.minusDays(20));
        ScheduleLesson l1 = lesson(2L, 1L, today.minusDays(19));
        ScheduleLesson l2 = lesson(3L, 1L, today.minusDays(18));
        ScheduleLesson l3 = lesson(4L, 1L, today.minusDays(17));
        ScheduleLesson l4 = lesson(5L, 1L, today.minusDays(16));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(l0, l1, l2, l3, l4));
        when(attendanceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(
                        attendance(1L, 10L, 1),
                        attendance(2L, 10L, 1),
                        attendance(3L, 10L, 1),
                        attendance(4L, 10L, 4),
                        attendance(5L, 10L, 4)));

        LessonAccount account = new LessonAccount();
        account.setStudentId(10L);
        account.setTotalLessons(new BigDecimal("20"));
        account.setRemainingLessons(new BigDecimal("3"));
        account.setExpireDate(today.plusDays(20));
        when(lessonAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        stubFollowupEmpty();

        List<RiskStudentVO> rows = riskWarningService.listRiskWarnings(null, null, null, null, null);

        assertThat(rows).hasSize(1);
        RiskStudentVO vo = rows.get(0);
        assertThat(vo.getStudentId()).isEqualTo(10L);
        assertThat(vo.getStudentName()).isEqualTo("小明");
        assertThat(vo.getClassName()).isEqualTo("钢琴A班");
        assertThat(vo.getRiskScore()).isEqualTo(68);
        assertThat(vo.getRiskLevel()).isEqualTo("HIGH");
        assertThat(vo.getF1()).isEqualTo(20);
        assertThat(vo.getF2()).isEqualTo(16);
        assertThat(vo.getF3()).isEqualTo(20);
        assertThat(vo.getF4()).isEqualTo(12);
        assertThat(vo.getAbsenceRate28d()).isEqualTo(40.0);
        assertThat(vo.getSuggestedAction()).contains("续费/到期提醒").contains("到课回访/出勤关怀");
        assertThat(vo.getFollowUpStatus()).isZero();
    }

    @Test
    @DisplayName("防误报：班级近45天无排课 → F1 不计分，名单为空")
    void listRiskWarnings_noRecentScheduleExcluded() {
        ClassStudent member = member(2L, 11L);
        stubBasicEnrollment(member, clazz(2L, 200L, "美术B班"), activeStudent(11L, "小红"));
        when(courseMapper.selectNamesByIdsIncludeDeleted(anyCollection()))
                .thenReturn(List.of(courseRow(200L, "美术")));
        // 无任何近期课次
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());
        LessonAccount account = new LessonAccount();
        account.setStudentId(11L);
        account.setTotalLessons(new BigDecimal("20"));
        account.setRemainingLessons(new BigDecimal("20"));
        when(lessonAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        stubFollowupEmpty();

        List<RiskStudentVO> rows = riskWarningService.listRiskWarnings(null, null, null, null, null);

        assertThat(rows).isEmpty();
        verify(attendanceMapper, never()).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("已退班学员不进入名单")
    void listRiskWarnings_withdrawnStudentExcluded() {
        ClassStudent member = member(3L, 12L);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(member));
        when(classGroupMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(clazz(3L, 300L, "舞蹈C班")));
        Student withdrawn = new Student();
        withdrawn.setId(12L);
        withdrawn.setStatus(4); // 已退班
        withdrawn.setName("小刚");
        when(studentMapper.selectBatchIds(anyCollection())).thenReturn(List.of(withdrawn));

        List<RiskStudentVO> rows = riskWarningService.listRiskWarnings(null, null, null, null, null);

        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("分页：按档位过滤返回正确分页")
    void pageRiskWarnings_filterByLevel() {
        LocalDate today = LocalDate.now();
        ClassStudent member = member(1L, 10L);
        stubBasicEnrollment(member, clazz(1L, 100L, "钢琴A班"), activeStudent(10L, "小明"));
        when(courseMapper.selectNamesByIdsIncludeDeleted(anyCollection()))
                .thenReturn(List.of(courseRow(100L, "钢琴")));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(lesson(1L, 1L, today.minusDays(20)),
                        lesson(2L, 1L, today.minusDays(18)),
                        lesson(3L, 1L, today.minusDays(16))));
        when(attendanceMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(attendance(1L, 10L, 1), attendance(2L, 10L, 1), attendance(3L, 10L, 4)));
        LessonAccount account = new LessonAccount();
        account.setStudentId(10L);
        account.setTotalLessons(new BigDecimal("20"));
        account.setRemainingLessons(new BigDecimal("3"));
        account.setExpireDate(today.plusDays(5));
        when(lessonAccountMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(account));
        stubFollowupEmpty();

        PageResult<RiskStudentVO> page = riskWarningService.pageRiskWarnings(1, 10, "HIGH", null, null, null, null);

        assertThat(page.getTotal()).isEqualTo(1);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0).getRiskLevel()).isEqualTo("HIGH");

        PageResult<RiskStudentVO> none = riskWarningService.pageRiskWarnings(1, 10, "LOW", null, null, null, null);
        assertThat(none.getTotal()).isZero();
    }

    @Test
    @DisplayName("跟进：无记录时新增，已跟进记录跟进时间")
    void updateFollowUp_insert() {
        when(followupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            StudentRiskFollowup fu = inv.getArgument(0, StudentRiskFollowup.class);
            fu.setId(1L);
            return 1;
        }).when(followupMapper).insert(any(StudentRiskFollowup.class));

        StudentRiskFollowup saved = riskWarningService.updateFollowUp(10L, 1, "已电话回访", 7L);

        assertThat(saved.getId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getFollowupTime()).isNotNull();
        verify(followupMapper).insert(any(StudentRiskFollowup.class));
    }

    @Test
    @DisplayName("跟进：已有记录走更新")
    void updateFollowUp_updateExisting() {
        StudentRiskFollowup existing = new StudentRiskFollowup();
        existing.setId(5L);
        existing.setStudentId(10L);
        existing.setStatus(0);
        when(followupMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        when(followupMapper.updateById(any(StudentRiskFollowup.class))).thenReturn(1);

        StudentRiskFollowup saved = riskWarningService.updateFollowUp(10L, 2, "暂不跟进", 7L);

        assertThat(saved.getStatus()).isEqualTo(2);
        assertThat(saved.getRemark()).isEqualTo("暂不跟进");
        verify(followupMapper, never()).insert(any(StudentRiskFollowup.class));
    }

    @Test
    @DisplayName("跟进：非法状态返回 400")
    void updateFollowUp_invalidStatus() {
        assertThatThrownBy(() -> riskWarningService.updateFollowUp(10L, 9, "x", 7L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("跟进状态只能为");
    }

    @Test
    @DisplayName("一键通知：按家长去重、当日幂等，返回通知数")
    void notifyParents_dedupeByParent() {
        ParentStudent linkA = new ParentStudent();
        linkA.setParentUserId(20L);
        linkA.setStudentId(10L);
        ParentStudent linkB = new ParentStudent();
        linkB.setParentUserId(21L);
        linkB.setStudentId(10L);
        ParentStudent linkC = new ParentStudent();
        linkC.setParentUserId(20L); // 同一家长关联两名学员 → 去重
        linkC.setStudentId(11L);
        when(parentStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(linkA, linkB, linkC));
        when(notificationService.sendOnce(eq(20L), any(Notification.class), anyString())).thenReturn(true);
        when(notificationService.sendOnce(eq(21L), any(Notification.class), anyString())).thenReturn(true);

        Map<String, Object> result = riskWarningService.notifyParents(List.of(10L, 11L), "请及时续费");

        assertThat(result.get("parentCount")).isEqualTo(2);
        assertThat(result.get("notifiedParentCount")).isEqualTo(2);
        // 同一家长关联两名学员只派发一次（去重）
        verify(notificationService, org.mockito.Mockito.times(1))
                .sendOnce(eq(20L), any(Notification.class), anyString());
        verify(notificationService, org.mockito.Mockito.times(1))
                .sendOnce(eq(21L), any(Notification.class), anyString());
    }

    @Test
    @DisplayName("一键通知：空学员列表返回 400")
    void notifyParents_emptyStudents() {
        assertThatThrownBy(() -> riskWarningService.notifyParents(Collections.emptyList(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请先选择学员");
    }
}
