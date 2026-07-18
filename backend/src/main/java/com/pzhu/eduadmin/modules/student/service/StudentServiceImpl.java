package com.pzhu.eduadmin.modules.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
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
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.dto.ParentBindingVO;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StudentServiceImpl implements StudentService {

    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final UserMapper userMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final com.pzhu.eduadmin.modules.exam.mapper.ExamSignupMapper examSignupMapper;

    private static final Map<String, SFunction<Student, ?>> STUDENT_SORT_MAP = Map.of(
            "id", Student::getId,
            "name", Student::getName,
            "birthday", Student::getBirthday,
            "status", Student::getStatus
    );

    @Override
    public Page<Student> pageStudents(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<Student> wrapper = new LambdaQueryWrapper<>();
        Set<Long> studentIdsByParent = findStudentIdsByParentName(keyword);
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> {
                w.like(Student::getName, keyword)
                        .or().like(Student::getContactPhone, keyword)
                        .or().like(Student::getSchool, keyword);
                if (!studentIdsByParent.isEmpty()) {
                    w.or().in(Student::getId, studentIdsByParent);
                }
            });
        }
        QueryHelper.applySort(wrapper, sortField, sortOrder, STUDENT_SORT_MAP, () -> wrapper.orderByDesc(Student::getId));
        Page<Student> page = studentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        enrichParentNames(page.getRecords());
        return page;
    }

    private Set<Long> findStudentIdsByParentName(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptySet();
        }
        List<User> parents = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "PARENT")
                        .like(User::getRealName, keyword));
        if (parents.isEmpty()) {
            return Collections.emptySet();
        }
        Set<Long> parentUserIds = parents.stream().map(User::getId).collect(Collectors.toSet());
        List<ParentStudent> bindings = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>().in(ParentStudent::getParentUserId, parentUserIds));
        return bindings.stream().map(ParentStudent::getStudentId).collect(Collectors.toSet());
    }

    private void enrichParentNames(List<Student> records) {
        if (records == null || records.isEmpty()) return;
        Set<Long> studentIds = records.stream().map(Student::getId).collect(Collectors.toSet());
        // 查 parent_student 关联表
        List<ParentStudent> bindings = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>().in(ParentStudent::getStudentId, studentIds));
        if (bindings.isEmpty()) return;
        // 收集所有 parentUserId
        Set<Long> parentUserIds = bindings.stream().map(ParentStudent::getParentUserId).collect(Collectors.toSet());
        // 查询父账号姓名
        Map<Long, String> userMap = parentUserIds.isEmpty()
                ? Collections.emptyMap()
                : userMapper.selectList(new LambdaQueryWrapper<User>().in(User::getId, parentUserIds))
                        .stream().collect(Collectors.toMap(User::getId, User::getRealName, (a, b) -> a));
        // 按 studentId 聚合家长姓名
        Map<Long, String> studentParentMap = new HashMap<>();
        for (ParentStudent b : bindings) {
            String parentName = userMap.getOrDefault(b.getParentUserId(), "用户" + b.getParentUserId());
            if (b.getRelation() != null && !b.getRelation().isEmpty()) {
                parentName = parentName + "(" + b.getRelation() + ")";
            }
            studentParentMap.merge(b.getStudentId(), parentName, (old, n) -> old + "、" + n);
        }
        // 回填到 Student
        records.forEach(s -> s.setParentName(studentParentMap.get(s.getId())));
    }

    @Override
    public Student getStudentById(Long id) {
        return studentMapper.selectById(id);
    }

    @Override
    public Student createStudent(Student student) {
        if (student.getName() == null || student.getName().isBlank()) {
            throw new BusinessException(400, "学员姓名不能为空");
        }
        studentMapper.insert(student);
        return student;
    }

    @Override
    public Student updateStudent(Student student) {
        Student existing = studentMapper.selectById(student.getId());
        if (existing == null) {
            throw new BusinessException(404, "学员不存在");
        }
        existing.setName(student.getName());
        existing.setGender(student.getGender());
        existing.setBirthday(student.getBirthday());
        existing.setSchool(student.getSchool());
        existing.setContactPhone(student.getContactPhone());
        studentMapper.updateById(existing);
        return studentMapper.selectById(student.getId());
    }

    @Override
    public boolean deleteStudent(Long id) {
        // 前置条件检查：不允许删除仍有在班记录或待处理报名的学员
        Long activeClassCount = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, id)
                        .eq(ClassStudent::getStatus, 1));
        if (activeClassCount > 0) {
            throw new BusinessException(409, "该学员仍有在班记录，无法删除");
        }

        Long pendingEnrollmentCount = enrollmentMapper.selectCount(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, id)
                        .in(Enrollment::getStatus, 1, 2));
        if (pendingEnrollmentCount > 0) {
            throw new BusinessException(409, "该学员仍有待处理的报名记录，无法删除");
        }

        // Issue #26: 检查是否有财务记录
        Long paymentCount = paymentRecordMapper.selectCount(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getStudentId, id));
        Long refundCount = refundRecordMapper.selectCount(
                new LambdaQueryWrapper<RefundRecord>().eq(RefundRecord::getStudentId, id));
        if (paymentCount > 0 || refundCount > 0) {
            throw new BusinessException(409, "该学员已有财务记录，无法删除");
        }

        // Issue #26: 检查是否有考级报名记录
        Long examCount = examSignupMapper.selectCount(
                new LambdaQueryWrapper<com.pzhu.eduadmin.modules.exam.entity.ExamSignup>()
                        .eq(com.pzhu.eduadmin.modules.exam.entity.ExamSignup::getStudentId, id));
        if (examCount > 0) {
            throw new BusinessException(409, "该学员已有考级报名记录，无法删除");
        }

        Student student = studentMapper.selectById(id);
        operationLogService.log("学员管理", "删除学员（学员=" + student.getName() + "）");
        return studentMapper.deleteById(id) > 0;
    }

    @Override
    public List<User> listParentOptions() {
        List<User> parents = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "PARENT")
                        .eq(User::getStatus, 1)
                        .orderByAsc(User::getRealName));
        parents.forEach(u -> u.setPassword(null));
        return parents;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean bindParent(ParentStudent parentStudent) {
        // 校验家长和学员是否存在
        if (userMapper.selectById(parentStudent.getParentUserId()) == null) {
            throw new BusinessException(404, "家长用户不存在");
        }
        if (studentMapper.selectById(parentStudent.getStudentId()) == null) {
            throw new BusinessException(404, "学员不存在");
        }

        // 防重复绑定（仅统计有效关系，已逻辑删除的历史行不算重复）
        Long count = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentStudent.getParentUserId())
                        .eq(ParentStudent::getStudentId, parentStudent.getStudentId()));
        if (count > 0) {
            throw new BusinessException(409, "该家长已绑定此学员，请勿重复绑定");
        }
        // 由于唯一键 uk_parent_student(parent_user_id, student_id) 对已软删行仍生效，
        // 若历史上曾绑定又被逻辑删除，直接 insert 会触发唯一键冲突。
        // 因此先物理清除该组合的所有历史行（含软删），再插入新行，保证可重复绑定/解绑。
        if (parentStudentMapper.countIncludingDeleted(parentStudent.getStudentId(), parentStudent.getParentUserId()) > 0) {
            parentStudentMapper.physicalDelete(parentStudent.getStudentId(), parentStudent.getParentUserId());
        }
        operationLogService.log("学员管理", "绑定家长（学员=" + nameResolver.getStudentName(parentStudent.getStudentId())
                + "，家长=" + nameResolver.getUserDisplayName(parentStudent.getParentUserId()) + "）");
        return parentStudentMapper.insert(parentStudent) > 0;
    }

    @Override
    public List<ParentBindingVO> listBoundParents(Long studentId) {
        List<ParentStudent> bindings = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getStudentId, studentId)
                        .orderByAsc(ParentStudent::getId));
        if (bindings.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> parentUserIds = bindings.stream()
                .map(ParentStudent::getParentUserId).collect(Collectors.toSet());
        Map<Long, User> userMap = userMapper.selectList(
                        new LambdaQueryWrapper<User>().in(User::getId, parentUserIds))
                .stream().collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
        List<ParentBindingVO> result = new ArrayList<>();
        for (ParentStudent b : bindings) {
            ParentBindingVO vo = new ParentBindingVO();
            vo.setId(b.getId());
            vo.setParentUserId(b.getParentUserId());
            vo.setRelation(b.getRelation());
            User u = userMap.get(b.getParentUserId());
            if (u != null) {
                vo.setRealName(u.getRealName());
                vo.setUsername(u.getUsername());
                vo.setPhone(u.getPhone());
            } else {
                vo.setRealName("用户" + b.getParentUserId());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public boolean unbindParent(Long studentId, Long parentUserId) {
        int rows = parentStudentMapper.physicalDelete(studentId, parentUserId);
        if (rows == 0) {
            throw new BusinessException(404, "未找到该家长的绑定关系");
        }
        operationLogService.log("学员管理", "解绑家长（学员=" + nameResolver.getStudentName(studentId)
                + "，家长=" + nameResolver.getUserDisplayName(parentUserId) + "）");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> transferStudent(Long studentId, Long targetClassId, Long fromClassId) {
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
        int maxCount = targetClass.getMaxStudentCount() != null ? targetClass.getMaxStudentCount() : 0;
        if (maxCount > 0 && targetStudentCount >= maxCount) {
            throw new BusinessException(409, "目标班级已满，无法转入");
        }

        // 5. Issue #22: 精确指定源班级 or 自动选择
        ClassStudent sourceRecord;
        if (fromClassId != null) {
            sourceRecord = currentRecords.stream()
                    .filter(r -> r.getClassId().equals(fromClassId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(404, "该学员不在指定的源班级中"));
        } else if (currentRecords.size() == 1) {
            sourceRecord = currentRecords.get(0);
        } else {
            throw new BusinessException(400,
                    "该学员在 " + currentRecords.size() + " 个班级中，请指定 fromClassId 参数");
        }

        // 6. 校验目标班级与源班级属于同一课程
        ClassGroup sourceClass = classGroupMapper.selectById(sourceRecord.getClassId());
        if (sourceClass == null) {
            throw new BusinessException(404, "源班级不存在");
        }
        if (!targetClass.getCourseId().equals(sourceClass.getCourseId())) {
            throw new BusinessException(400, "目标班级必须属于同一课程");
        }

        // 7. 事务操作：源班级记录标记为已转出
        sourceRecord.setStatus(2); // 已转出
        classStudentMapper.updateById(sourceRecord);

        ClassStudent newRecord = new ClassStudent();
        newRecord.setClassId(targetClassId);
        newRecord.setStudentId(studentId);
        newRecord.setStatus(1);
        newRecord.setJoinTime(LocalDateTime.now());
        classStudentMapper.insert(newRecord);

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("fromClassId", sourceRecord.getClassId());
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
        if (student.getStatus() != null && student.getStatus() == 4) {
            throw new BusinessException(409, "该学员已退班，请勿重复操作");
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

        // 3.5 更新学员状态为已退班
        student.setStatus(4);
        studentMapper.updateById(student);

        // 4. 查找该学员最近的报名和缴费记录，生成退费申请
        PaymentRecord latestPayment = paymentRecordMapper.selectPage(
                new Page<PaymentRecord>(1, 1),
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getStudentId, studentId)
                        .orderByDesc(PaymentRecord::getPayTime))
                .getRecords().stream().findFirst().orElse(null);

        RefundRecord refund = new RefundRecord();
        com.pzhu.eduadmin.security.LoginUser operator = CurrentUserHolder.get();
        refund.setStudentId(studentId);
        refund.setApplicantId(operator != null ? operator.getUserId() : 0L);
        refund.setApplicantRole(operator != null ? operator.getRoleCode() : "SYSTEM");
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

}
