package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.student.service.StudentServiceImpl;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * StudentServiceImpl 单元测试。
 * 使用 Mockito mock 所有 Mapper 依赖，覆盖核心业务方法的正常与异常路径。
 */
@ExtendWith(MockitoExtension.class)
class StudentServiceMockTest {

    @Mock
    private StudentMapper studentMapper;

    @Mock
    private ParentStudentMapper parentStudentMapper;

    @Mock
    private UserMapper userMapper;

    @Mock
    private ClassStudentMapper classStudentMapper;

    @Mock
    private ClassGroupMapper classGroupMapper;

    @Mock
    private PaymentRecordMapper paymentRecordMapper;

    @Mock
    private RefundRecordMapper refundRecordMapper;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private OperationLogService operationLogService;
    @Mock
    private EntityNameResolver nameResolver;
    @Mock
    private ExamSignupMapper examSignupMapper;

    @InjectMocks
    private StudentServiceImpl studentService;

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
    }

    // ========== createStudent ==========

    @Test
    void createStudent_NormalCreate_Success() {
        Student student = new Student();
        student.setName("张三");
        student.setGender(1);
        student.setContactPhone("13800138000");

        when(studentMapper.insert(any(Student.class))).thenReturn(1);

        Student result = studentService.createStudent(student);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三");
        verify(studentMapper).insert(student);
    }

    @Test
    void createStudent_BlankName_ThrowsException() {
        Student student = new Student();
        student.setName("");

        assertThatThrownBy(() -> studentService.createStudent(student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员姓名不能为空");

        verify(studentMapper, never()).insert(any(Student.class));
    }

    @Test
    void createStudent_NullName_ThrowsException() {
        Student student = new Student();
        student.setName(null);

        assertThatThrownBy(() -> studentService.createStudent(student))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员姓名不能为空");

        verify(studentMapper, never()).insert(any(Student.class));
    }

    // ========== updateStudent ==========

    @Test
    void updateStudent_NormalUpdate_Success() {
        Student existing = new Student();
        existing.setId(1L);
        existing.setName("张三");
        existing.setGender(1);

        Student input = new Student();
        input.setId(1L);
        input.setName("张三丰");
        input.setGender(1);
        input.setSchool("华中科技大学");
        input.setContactPhone("13900139000");

        Student updated = new Student();
        updated.setId(1L);
        updated.setName("张三丰");
        updated.setSchool("华中科技大学");

        when(studentMapper.selectById(1L)).thenReturn(existing, updated);
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);

        Student result = studentService.updateStudent(input);

        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo("张三丰");
        verify(studentMapper).updateById(any(Student.class));
    }

    @Test
    void updateStudent_StudentNotFound_ThrowsException() {
        Student input = new Student();
        input.setId(999L);
        input.setName("不存在");

        when(studentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> studentService.updateStudent(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员不存在")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);

        verify(studentMapper, never()).updateById(any(Student.class));
    }

    // ========== deleteStudent ==========

    @Test
    void deleteStudent_NormalDelete_Success() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");

        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(studentMapper.selectById(1L)).thenReturn(student);
        when(studentMapper.deleteById(1L)).thenReturn(1);

        boolean result = studentService.deleteStudent(1L);

        assertThat(result).isTrue();
        verify(studentMapper).deleteById(1L);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    void deleteStudent_WithActiveClassRecords_ThrowsException() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> studentService.deleteStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("在班记录")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(studentMapper, never()).deleteById(any(Student.class));
    }

    @Test
    void deleteStudent_WithPendingEnrollments_ThrowsException() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> studentService.deleteStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("待处理的报名记录")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(studentMapper, never()).deleteById(any(Student.class));
    }

    @Test
    void deleteStudent_WithPaymentRecords_ThrowsException() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> studentService.deleteStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("财务记录")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(studentMapper, never()).deleteById(any(Student.class));
    }

    @Test
    void deleteStudent_WithRefundRecordsOnly_ThrowsException() {
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(enrollmentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(paymentRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(refundRecordMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> studentService.deleteStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("财务记录");

        verify(studentMapper, never()).deleteById(any(Student.class));
    }

    // ========== bindParent ==========

    @Test
    void bindParent_NormalBind_Success() {
        ParentStudent ps = new ParentStudent();
        ps.setParentUserId(10L);
        ps.setStudentId(20L);
        ps.setRelation("父亲");

        User parentUser = new User();
        parentUser.setId(10L);
        parentUser.setRealName("张父");
        parentUser.setRoleCode("PARENT");
        parentUser.setStatus(1);

        Student student = new Student();
        student.setId(20L);
        student.setName("张三");

        when(userMapper.selectById(10L)).thenReturn(parentUser);
        when(studentMapper.selectById(20L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(parentStudentMapper.countIncludingDeleted(20L, 10L)).thenReturn(0);
        when(parentStudentMapper.insert(any(ParentStudent.class))).thenReturn(1);
        when(nameResolver.getStudentName(anyLong())).thenReturn("学员A");
        when(nameResolver.getUserDisplayName(anyLong())).thenReturn("家长B");

        boolean result = studentService.bindParent(ps);

        assertThat(result).isTrue();
        verify(parentStudentMapper).insert(ps);
        verify(parentStudentMapper, never()).physicalDelete(any(), any());
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    void bindParent_WithSoftDeletedHistory_CleansUpAndBinds() {
        ParentStudent ps = new ParentStudent();
        ps.setParentUserId(10L);
        ps.setStudentId(20L);
        ps.setRelation("母亲");

        User parentUser = new User();
        parentUser.setId(10L);
        parentUser.setRoleCode("PARENT");
        parentUser.setStatus(1);
        Student student = new Student();
        student.setId(20L);

        when(userMapper.selectById(10L)).thenReturn(parentUser);
        when(studentMapper.selectById(20L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(parentStudentMapper.countIncludingDeleted(20L, 10L)).thenReturn(1);
        when(parentStudentMapper.physicalDelete(20L, 10L)).thenReturn(1);
        when(parentStudentMapper.insert(any(ParentStudent.class))).thenReturn(1);
        when(nameResolver.getStudentName(anyLong())).thenReturn("学员A");
        when(nameResolver.getUserDisplayName(anyLong())).thenReturn("家长B");

        boolean result = studentService.bindParent(ps);

        assertThat(result).isTrue();
        verify(parentStudentMapper).physicalDelete(20L, 10L);
        verify(parentStudentMapper).insert(ps);
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    void bindParent_DuplicateBinding_ThrowsException() {
        ParentStudent ps = new ParentStudent();
        ps.setParentUserId(10L);
        ps.setStudentId(20L);

        User parentUser = new User();
        parentUser.setId(10L);
        parentUser.setRoleCode("PARENT");
        parentUser.setStatus(1);
        Student student = new Student();
        student.setId(20L);

        when(userMapper.selectById(10L)).thenReturn(parentUser);
        when(studentMapper.selectById(20L)).thenReturn(student);
        when(parentStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> studentService.bindParent(ps))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已绑定此学员")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    void bindParent_ParentNotFound_ThrowsException() {
        ParentStudent ps = new ParentStudent();
        ps.setParentUserId(999L);
        ps.setStudentId(20L);

        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> studentService.bindParent(ps))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("家长用户不存在")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);

        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    @Test
    void bindParent_StudentNotFound_ThrowsException() {
        ParentStudent ps = new ParentStudent();
        ps.setParentUserId(10L);
        ps.setStudentId(999L);

        User parentUser = new User();
        parentUser.setId(10L);
        parentUser.setRoleCode("PARENT");
        parentUser.setStatus(1);

        when(userMapper.selectById(10L)).thenReturn(parentUser);
        when(studentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> studentService.bindParent(ps))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员不存在")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);

        verify(parentStudentMapper, never()).insert(any(ParentStudent.class));
    }

    // ========== transferStudent ==========

    @Test
    void transferStudent_NormalTransfer_Success() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");

        ClassStudent sourceRecord = new ClassStudent();
        sourceRecord.setId(100L);
        sourceRecord.setClassId(10L);
        sourceRecord.setStudentId(1L);
        sourceRecord.setStatus(1);

        ClassGroup targetClass = new ClassGroup();
        targetClass.setId(20L);
        targetClass.setCourseId(5L);
        targetClass.setMaxStudentCount(30);

        ClassGroup sourceClass = new ClassGroup();
        sourceClass.setId(10L);
        sourceClass.setCourseId(5L);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sourceRecord));
        when(classGroupMapper.selectById(20L)).thenReturn(targetClass);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(10L);
        when(classGroupMapper.selectById(10L)).thenReturn(sourceClass);
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        when(classStudentMapper.insert(any(ClassStudent.class))).thenReturn(1);

        Map<String, Object> result = studentService.transferStudent(1L, 20L, null);

        assertThat(result).containsEntry("studentId", 1L);
        assertThat(result).containsEntry("fromClassId", 10L);
        assertThat(result).containsEntry("targetClassId", 20L);
        assertThat(result).containsEntry("message", "转班成功");

        // 源记录状态应被标记为已转出(2)
        assertThat(sourceRecord.getStatus()).isEqualTo(2);
        verify(classStudentMapper).updateById(sourceRecord);

        // 应创建新的在班记录
        ArgumentCaptor<ClassStudent> newRecordCaptor = ArgumentCaptor.forClass(ClassStudent.class);
        verify(classStudentMapper).insert(newRecordCaptor.capture());
        ClassStudent newRecord = newRecordCaptor.getValue();
        assertThat(newRecord.getClassId()).isEqualTo(20L);
        assertThat(newRecord.getStudentId()).isEqualTo(1L);
        assertThat(newRecord.getStatus()).isEqualTo(1);
    }

    @Test
    void transferStudent_WithExplicitFromClassId_Success() {
        Student student = new Student();
        student.setId(1L);

        ClassStudent record1 = new ClassStudent();
        record1.setId(100L);
        record1.setClassId(10L);
        record1.setStudentId(1L);
        record1.setStatus(1);

        ClassStudent record2 = new ClassStudent();
        record2.setId(101L);
        record2.setClassId(11L);
        record2.setStudentId(1L);
        record2.setStatus(1);

        ClassGroup targetClass = new ClassGroup();
        targetClass.setId(20L);
        targetClass.setCourseId(5L);
        targetClass.setMaxStudentCount(30);

        ClassGroup sourceClass = new ClassGroup();
        sourceClass.setId(11L);
        sourceClass.setCourseId(5L);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record1, record2));
        when(classGroupMapper.selectById(20L)).thenReturn(targetClass);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(classGroupMapper.selectById(11L)).thenReturn(sourceClass);
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        when(classStudentMapper.insert(any(ClassStudent.class))).thenReturn(1);

        Map<String, Object> result = studentService.transferStudent(1L, 20L, 11L);

        assertThat(result).containsEntry("fromClassId", 11L);
        assertThat(result).containsEntry("message", "转班成功");
    }

    @Test
    void transferStudent_TargetClassFull_ThrowsException() {
        Student student = new Student();
        student.setId(1L);

        ClassStudent sourceRecord = new ClassStudent();
        sourceRecord.setId(100L);
        sourceRecord.setClassId(10L);
        sourceRecord.setStudentId(1L);
        sourceRecord.setStatus(1);

        ClassGroup targetClass = new ClassGroup();
        targetClass.setId(20L);
        targetClass.setCourseId(5L);
        targetClass.setMaxStudentCount(2);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sourceRecord));
        when(classGroupMapper.selectById(20L)).thenReturn(targetClass);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> studentService.transferStudent(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("目标班级已满")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void transferStudent_DifferentCourse_ThrowsException() {
        Student student = new Student();
        student.setId(1L);

        ClassStudent sourceRecord = new ClassStudent();
        sourceRecord.setId(100L);
        sourceRecord.setClassId(10L);
        sourceRecord.setStudentId(1L);
        sourceRecord.setStatus(1);

        ClassGroup targetClass = new ClassGroup();
        targetClass.setId(20L);
        targetClass.setCourseId(6L); // courseId = 6
        targetClass.setMaxStudentCount(30);

        ClassGroup sourceClass = new ClassGroup();
        sourceClass.setId(10L);
        sourceClass.setCourseId(5L); // courseId = 5，不同课程

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(sourceRecord));
        when(classGroupMapper.selectById(20L)).thenReturn(targetClass);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        when(classGroupMapper.selectById(10L)).thenReturn(sourceClass);

        assertThatThrownBy(() -> studentService.transferStudent(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("同一课程");

        verify(classStudentMapper, never()).insert(any(ClassStudent.class));
    }

    @Test
    void transferStudent_NoActiveClassRecords_ThrowsException() {
        Student student = new Student();
        student.setId(1L);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> studentService.transferStudent(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有在班记录");

        verify(classGroupMapper, never()).selectById(any());
    }

    @Test
    void transferStudent_MultipleRecordsWithoutFromClassId_ThrowsException() {
        Student student = new Student();
        student.setId(1L);

        ClassStudent record1 = new ClassStudent();
        record1.setId(100L);
        record1.setClassId(10L);
        record1.setStudentId(1L);
        record1.setStatus(1);

        ClassStudent record2 = new ClassStudent();
        record2.setId(101L);
        record2.setClassId(11L);
        record2.setStudentId(1L);
        record2.setStatus(1);

        ClassGroup targetClass = new ClassGroup();
        targetClass.setId(20L);
        targetClass.setCourseId(5L);
        targetClass.setMaxStudentCount(30);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(record1, record2));
        when(classGroupMapper.selectById(20L)).thenReturn(targetClass);
        when(classStudentMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);

        // 学员在2个班级中，未指定 fromClassId，应抛出异常
        assertThatThrownBy(() -> studentService.transferStudent(1L, 20L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请指定 fromClassId");
    }

    // ========== withdrawStudent ==========

    @Test
    void withdrawStudent_NormalWithdraw_CreatesRefundRecord() {
        Student student = new Student();
        student.setId(1L);
        student.setName("张三");
        student.setStatus(1);

        ClassStudent cs = new ClassStudent();
        cs.setId(100L);
        cs.setClassId(10L);
        cs.setStudentId(1L);
        cs.setStatus(1);

        PaymentRecord payment = new PaymentRecord();
        payment.setId(50L);
        payment.setEnrollmentId(30L);
        payment.setStudentId(1L);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(cs));
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);
        // 改为 selectPage（跨数据库兼容）
        Page<PaymentRecord> page = new Page<>(1, 1);
        page.setRecords(List.of(payment));
        when(paymentRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(page);
        when(refundRecordMapper.insert(any(RefundRecord.class))).thenAnswer(invocation -> {
            RefundRecord r = invocation.getArgument(0);
            r.setId(100L);
            return 1;
        });

        Map<String, Object> result = studentService.withdrawStudent(1L);

        assertThat(result).containsEntry("studentId", 1L);
        assertThat(result).containsEntry("message", "退班申请已提交，待财务审核退费");
        assertThat(result.get("refundRecordId")).isEqualTo(100L);

        // 在班记录应被标记为已退出(3)
        assertThat(cs.getStatus()).isEqualTo(3);
        verify(classStudentMapper).updateById(cs);

        // 学员状态应被标记为已退班(4)
        assertThat(student.getStatus()).isEqualTo(4);
        verify(studentMapper).updateById(student);

        // 应创建退费申请，且关联到最近缴费记录
        ArgumentCaptor<RefundRecord> refundCaptor = ArgumentCaptor.forClass(RefundRecord.class);
        verify(refundRecordMapper).insert(refundCaptor.capture());
        RefundRecord refund = refundCaptor.getValue();
        assertThat(refund.getStudentId()).isEqualTo(1L);
        assertThat(refund.getStatus()).isEqualTo(1); // 待审核
        assertThat(refund.getAmount()).isEqualTo(BigDecimal.ZERO);
        assertThat(refund.getLessonCount()).isEqualTo(BigDecimal.ZERO);
        assertThat(refund.getPaymentRecordId()).isEqualTo(50L);
        assertThat(refund.getEnrollmentId()).isEqualTo(30L);
        assertThat(refund.getApplicantId()).isEqualTo(1L); // CurrentUserHolder 中的 admin
        assertThat(refund.getApplicantRole()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    void withdrawStudent_NoPaymentRecord_StillCreatesRefund() {
        Student student = new Student();
        student.setId(1L);
        student.setStatus(1);

        ClassStudent cs = new ClassStudent();
        cs.setId(100L);
        cs.setClassId(10L);
        cs.setStudentId(1L);
        cs.setStatus(1);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(cs));
        when(classStudentMapper.updateById(any(ClassStudent.class))).thenReturn(1);
        when(studentMapper.updateById(any(Student.class))).thenReturn(1);
        // 改为 selectPage（跨数据库兼容）
        Page<PaymentRecord> emptyPage = new Page<>(1, 1);
        emptyPage.setRecords(Collections.emptyList());
        when(paymentRecordMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(emptyPage);

        Map<String, Object> result = studentService.withdrawStudent(1L);

        // L9: 无缴费记录时不创建退费申请
        assertThat(result).containsEntry("message", "退班完成，该学员无缴费记录");
        assertThat(result).containsEntry("refundRecordId", null);
        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }

    @Test
    void withdrawStudent_StudentNotFound_ThrowsException() {
        when(studentMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> studentService.withdrawStudent(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("学员不存在")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(404);

        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }

    @Test
    void withdrawStudent_AlreadyWithdrawn_ThrowsException() {
        Student student = new Student();
        student.setId(1L);
        student.setStatus(4); // 已退班

        when(studentMapper.selectById(1L)).thenReturn(student);

        assertThatThrownBy(() -> studentService.withdrawStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已退班")
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(409);

        verify(classStudentMapper, never()).updateById(any(ClassStudent.class));
        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }

    @Test
    void withdrawStudent_NoActiveClassRecords_ThrowsException() {
        Student student = new Student();
        student.setId(1L);
        student.setStatus(1);

        when(studentMapper.selectById(1L)).thenReturn(student);
        when(classStudentMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> studentService.withdrawStudent(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有在班记录");

        verify(refundRecordMapper, never()).insert(any(RefundRecord.class));
    }
}
