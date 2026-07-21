package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.mapper.LeaveRequestMapper;
import com.pzhu.eduadmin.modules.attendance.service.LeaveRequestServiceImpl;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("请假服务 Mock 单元测试")
class LeaveRequestServiceMockTest {

    @Mock private LeaveRequestMapper leaveRequestMapper;
    @Mock private StudentMapper studentMapper;
    @Mock private ParentStudentMapper parentStudentMapper;
    @Mock private AttendanceMapper attendanceMapper;
    @Mock private ScheduleLessonMapper scheduleLessonMapper;
    @Mock private ClassStudentMapper classStudentMapper;

    @InjectMocks
    private LeaveRequestServiceImpl leaveRequestService;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LeaveRequest.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Student.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ParentStudent.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), Attendance.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ScheduleLesson.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), ClassStudent.class);
    }

    // ============ submitLeaveRequest ============

    @Test
    @DisplayName("正常提交请假 — 学员存在、有绑定关系、无重复")
    void submit_Success() {
        Student student = new Student();
        student.setId(10L);
        student.setName("张三");
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(leaveRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> { inv.getArgument(0, LeaveRequest.class).setId(100L); return 1; })
                .when(leaveRequestMapper).insert(any(LeaveRequest.class));

        LeaveRequest result = leaveRequestService.submitLeaveRequest(1L, 10L,
                LocalDate.now().plusDays(7), "身体不适");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getStudentId()).isEqualTo(10L);
        assertThat(result.getParentUserId()).isEqualTo(1L);
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getReason()).isEqualTo("身体不适");
    }

    @Test
    @DisplayName("学员不存在 — 抛出 404")
    void submit_StudentNotFound() {
        when(studentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> leaveRequestService.submitLeaveRequest(1L, 999L,
                LocalDate.now().plusDays(7), "原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员不存在");
    }

    @Test
    @DisplayName("家长与学员无绑定关系 — 抛出 403")
    void submit_NoBinding() {
        Student student = new Student();
        student.setId(10L);
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> leaveRequestService.submitLeaveRequest(1L, 10L,
                LocalDate.now().plusDays(7), "原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有绑定关系");
    }

    @Test
    @DisplayName("同一天已有待审核请假 — 抛出 409")
    void submit_DuplicatePending() {
        Student student = new Student();
        student.setId(10L);
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(leaveRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> leaveRequestService.submitLeaveRequest(1L, 10L,
                LocalDate.now().plusDays(7), "原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有请假申请");
    }

    @Test
    @DisplayName("同一天已有已通过请假 — 抛出 409")
    void submit_DuplicateApproved() {
        Student student = new Student();
        student.setId(10L);
        when(studentMapper.selectById(10L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // selectCount 返回 >0 表示已存在 status=1 或 2 的请假
        when(leaveRequestMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> leaveRequestService.submitLeaveRequest(1L, 10L,
                LocalDate.now().plusDays(7), "原因"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有请假申请");
    }

    // ============ getByParent ============

    @Test
    @DisplayName("按家长查询请假列表 — 含学员名称填充")
    void getByParent_Success() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        lr.setStatus(1);
        when(leaveRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lr));
        when(studentMapper.selectNamesByIdsIncludeDeleted(anySet()))
                .thenReturn(List.of(Map.of("id", 10L, "name", "张三")));

        List<LeaveRequest> result = leaveRequestService.getByParent(1L, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStudentName()).isEqualTo("张三");
    }

    @Test
    @DisplayName("按家长和学员过滤查询")
    void getByParent_WithStudentId() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        when(leaveRequestMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(lr));
        when(studentMapper.selectNamesByIdsIncludeDeleted(anySet()))
                .thenReturn(List.of(Map.of("id", 10L, "name", "张三")));

        List<LeaveRequest> result = leaveRequestService.getByParent(1L, 10L);

        assertThat(result).hasSize(1);
    }

    // ============ pageAll ============

    @Test
    @DisplayName("分页查询无关键词 — 返回含名称填充的分页结果")
    void pageAll_NoKeyword() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        Page<LeaveRequest> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(lr));
        when(leaveRequestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(studentMapper.selectNamesByIdsIncludeDeleted(anySet()))
                .thenReturn(Collections.emptyList());

        Page<LeaveRequest> result = leaveRequestService.pageAll(1, 10, null);

        assertThat(result.getRecords()).hasSize(1);
    }

    @Test
    @DisplayName("按关键词搜索 — 匹配原因或学员名")
    void pageAll_WithKeyword() {
        Student s = new Student();
        s.setId(10L);
        s.setName("张三");
        when(studentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(s));

        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        Page<LeaveRequest> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(lr));
        when(leaveRequestMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(mockPage);
        when(studentMapper.selectNamesByIdsIncludeDeleted(anySet()))
                .thenReturn(List.of(Map.of("id", 10L, "name", "张三")));

        Page<LeaveRequest> result = leaveRequestService.pageAll(1, 10, "张三");

        assertThat(result.getRecords()).hasSize(1);
    }

    // ============ audit ============

    @Test
    @DisplayName("审核通过 — status 变为 2 并自动创建考勤记录")
    void audit_Approve() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        lr.setLessonDate(LocalDate.now().plusDays(7));
        lr.setStatus(1);
        when(leaveRequestMapper.selectById(1L)).thenReturn(lr);
        when(leaveRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        // 自动创建考勤：查找学员班级
        ClassStudent cs = new ClassStudent();
        cs.setClassId(5L);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cs));

        // 查找课次
        ScheduleLesson lesson = new ScheduleLesson();
        lesson.setId(50L);
        lesson.setClassId(5L);
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(lesson));

        // 无已有考勤
        when(attendanceMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        doAnswer(inv -> { inv.getArgument(0, Attendance.class).setId(200L); return 1; })
                .when(attendanceMapper).insert(any(Attendance.class));

        LeaveRequest result = leaveRequestService.audit(1L, 2, 99L, "同意");

        assertThat(result.getStatus()).isEqualTo(2);
        assertThat(result.getAuditUserId()).isEqualTo(99L);
        assertThat(result.getAuditRemark()).isEqualTo("同意");
        assertThat(result.getScheduleId()).isEqualTo(50L);
        verify(attendanceMapper).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("审核驳回 — status 变为 3，不创建考勤")
    void audit_Reject() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        lr.setStatus(1);
        when(leaveRequestMapper.selectById(1L)).thenReturn(lr);
        when(leaveRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        LeaveRequest result = leaveRequestService.audit(1L, 3, 99L, "不同意");

        assertThat(result.getStatus()).isEqualTo(3);
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }

    @Test
    @DisplayName("审核状态非法 — 抛出 400")
    void audit_InvalidStatus() {
        assertThatThrownBy(() -> leaveRequestService.audit(1L, 1, 99L, "备注"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("审核状态只能为2");
    }

    @Test
    @DisplayName("请假记录不存在 — 抛出 404")
    void audit_NotFound() {
        when(leaveRequestMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> leaveRequestService.audit(999L, 2, 99L, "备注"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请假记录不存在");
    }

    @Test
    @DisplayName("请假已审核不可重复操作 — 抛出 409")
    void audit_AlreadyAudited() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStatus(2); // 已通过
        when(leaveRequestMapper.selectById(1L)).thenReturn(lr);

        assertThatThrownBy(() -> leaveRequestService.audit(1L, 2, 99L, "备注"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已审核");
    }

    @Test
    @DisplayName("审核通过但无在班记录 — 不创建考勤不崩溃")
    void audit_ApproveNoEnrollment() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        lr.setLessonDate(LocalDate.now().plusDays(7));
        lr.setStatus(1);
        when(leaveRequestMapper.selectById(1L)).thenReturn(lr);
        when(leaveRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        LeaveRequest result = leaveRequestService.audit(1L, 2, 99L, "同意");

        assertThat(result.getStatus()).isEqualTo(2);
        verify(attendanceMapper, never()).insert(any(Attendance.class));
        verify(scheduleLessonMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("审核通过但当天无排课 — 不创建考勤")
    void audit_ApproveNoLesson() {
        LeaveRequest lr = new LeaveRequest();
        lr.setId(1L);
        lr.setStudentId(10L);
        lr.setLessonDate(LocalDate.now().plusDays(7));
        lr.setStatus(1);
        when(leaveRequestMapper.selectById(1L)).thenReturn(lr);
        when(leaveRequestMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);

        ClassStudent cs = new ClassStudent();
        cs.setClassId(5L);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(cs));
        when(scheduleLessonMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        LeaveRequest result = leaveRequestService.audit(1L, 2, 99L, "同意");

        assertThat(result.getStatus()).isEqualTo(2);
        verify(attendanceMapper, never()).insert(any(Attendance.class));
    }
}
