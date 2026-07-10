package com.pzhu.eduadmin.modules.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final OperationLogMapper operationLogMapper;

    @Override
    public Page<Student> pageStudents(int pageNum, int pageSize) {
        return studentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<>());
    }

    @Override
    public Student getStudentById(Long id) {
        return studentMapper.selectById(id);
    }

    @Override
    public Student createStudent(Student student) {
        studentMapper.insert(student);
        return student;
    }

    @Override
    public Student updateStudent(Student student) {
        studentMapper.updateById(student);
        return studentMapper.selectById(student.getId());
    }

    @Override
    public boolean deleteStudent(Long id) {
        logOperation("学员管理", "删除学员(id=" + id + ")");
        return studentMapper.deleteById(id) > 0;
    }

    @Override
    public boolean bindParent(ParentStudent parentStudent) {
        // 防重复绑定
        Long count = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentStudent.getParentUserId())
                        .eq(ParentStudent::getStudentId, parentStudent.getStudentId()));
        if (count > 0) {
            throw new BusinessException(409, "该家长已绑定此学员，请勿重复绑定");
        }
        return parentStudentMapper.insert(parentStudent) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferStudent(Long studentId, Long targetClassId) {
        // 1. 校验学员存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }

        // 2. 查找当前在班记录
        List<ClassStudent> currentRecords = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, studentId)
                        .eq(ClassStudent::getStatus, 1));
        if (currentRecords.isEmpty()) {
            throw new BusinessException(400, "该学员当前没有在班记录");
        }

        // 3. 校验目标班级存在
        ClassGroup targetClass = classGroupMapper.selectById(targetClassId);
        if (targetClass == null) {
            throw new BusinessException(404, "目标班级不存在");
        }

        // 4. 校验目标班级容量
        Long targetStudentCount = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, targetClassId)
                        .eq(ClassStudent::getStatus, 1));
        if (targetStudentCount >= targetClass.getMaxStudentCount()) {
            throw new BusinessException(409, "目标班级已满，无法转入");
        }

        // 5. 事务操作：旧记录置为已转出，插入新记录
        for (ClassStudent cs : currentRecords) {
            cs.setStatus(2); // 已转出
            classStudentMapper.updateById(cs);
        }

        ClassStudent newRecord = new ClassStudent();
        newRecord.setClassId(targetClassId);
        newRecord.setStudentId(studentId);
        newRecord.setStatus(1);
        newRecord.setJoinTime(LocalDateTime.now());
        classStudentMapper.insert(newRecord);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("fromClassIds", currentRecords.stream().map(ClassStudent::getClassId).toList());
        result.put("targetClassId", targetClassId);
        result.put("message", "转班成功");
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> withdrawStudent(Long studentId) {
        // 1. 校验学员存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }

        // 2. 查找当前在班记录
        List<ClassStudent> currentRecords = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, studentId)
                        .eq(ClassStudent::getStatus, 1));
        if (currentRecords.isEmpty()) {
            throw new BusinessException(400, "该学员当前没有在班记录");
        }

        // 3. 所有在班记录置为已退出
        for (ClassStudent cs : currentRecords) {
            cs.setStatus(3); // 已退出
            classStudentMapper.updateById(cs);
        }

        // 4. 查找该学员最近的报名和缴费记录，生成退费申请
        PaymentRecord latestPayment = paymentRecordMapper.selectOne(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getStudentId, studentId)
                        .orderByDesc(PaymentRecord::getPayTime)
                        .last("LIMIT 1"));

        RefundRecord refund = new RefundRecord();
        refund.setStudentId(studentId);
        refund.setApplicantId(CurrentUserHolder.get().getUserId());
        refund.setApplicantRole(CurrentUserHolder.get().getRoleCode());
        refund.setStatus(1); // 待审核
        refund.setAmount(BigDecimal.ZERO); // 退费金额由财务审核时确定
        refund.setLessonCount(BigDecimal.ZERO);

        if (latestPayment != null) {
            refund.setPaymentRecordId(latestPayment.getId());
            refund.setEnrollmentId(latestPayment.getEnrollmentId());
        }
        refundRecordMapper.insert(refund);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("withdrawnClassIds", currentRecords.stream().map(ClassStudent::getClassId).toList());
        result.put("refundRecordId", refund.getId());
        result.put("message", "退班申请已提交，待财务审核退费");
        return result;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
