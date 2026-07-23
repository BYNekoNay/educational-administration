package com.pzhu.eduadmin.modules.student.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
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
import org.springframework.dao.DuplicateKeyException;
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
                        .stream().collect(Collectors.toMap(User::getId,
                                u -> u.getRealName() != null && !u.getRealName().isBlank()
                                        ? u.getRealName()
                                        : (u.getUsername() != null ? u.getUsername() : "用户" + u.getId()),
                                (a, b) -> a));
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
        // M fix: null 安全白名单，防止部分更新（partial PUT）把未携带的字段覆盖为 null
        if (student.getName() != null) {
            if (student.getName().isBlank()) {
                throw new BusinessException(400, "学员姓名不能为空");
            }
            existing.setName(student.getName());
        }
        if (student.getGender() != null) existing.setGender(student.getGender());
        if (student.getBirthday() != null) existing.setBirthday(student.getBirthday());
        if (student.getSchool() != null) existing.setSchool(student.getSchool());
        if (student.getContactPhone() != null) existing.setContactPhone(student.getContactPhone());
        studentMapper.updateById(existing);
        return studentMapper.selectById(student.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
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
        // H5 fix: 防止学员不存在时 NPE
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }
        operationLogService.log("学员管理", "删除学员（学员=" + student.getName() + "）");
        // A2#5 fix: 清理由该学员的家长绑定关系，避免遗留孤立 parent_student 行
        parentStudentMapper.delete(new LambdaQueryWrapper<ParentStudent>()
                .eq(ParentStudent::getStudentId, id));
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
        User parentUser = userMapper.selectById(parentStudent.getParentUserId());
        if (parentUser == null) {
            throw new BusinessException(404, "家长用户不存在");
        }
        // Bug #35: 校验用户确实是家长角色且未被禁用
        if (!"PARENT".equals(parentUser.getRoleCode())) {
            throw new BusinessException(400, "只能绑定家长角色的用户");
        }
        if (parentUser.getStatus() != null && parentUser.getStatus() != 1) {
            throw new BusinessException(400, "该用户已被禁用");
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
        try {
            return parentStudentMapper.insert(parentStudent) > 0;
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该家长已绑定此学员，请勿重复绑定");
        }
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
        // Low fix: 日志记录失败不应影响解绑业务操作
        try {
            operationLogService.log("学员管理", "解绑家长（学员=" + nameResolver.getStudentName(studentId)
                    + "，家长=" + nameResolver.getUserDisplayName(parentUserId) + "）");
        } catch (Exception ignored) {
            // 日志异常不阻断主流程
        }
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
        // Medium fix: 目标班级必须为开班状态（status=1），禁止转入已结课/已关闭班级
        if (targetClass.getStatus() != null && targetClass.getStatus() != 1) {
            throw new BusinessException(409, "目标班级非开班状态，无法转入");
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

        // Low fix: 禁止转入当前所在班级（无意义的同班转班）
        if (targetClassId.equals(sourceRecord.getClassId())) {
            throw new BusinessException(400, "目标班级与当前班级相同，无需转班");
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

        // H1 fix: class_student 唯一键 uk_class_student(class_id, student_id) 不含 status，
        // 学员转回曾经离开过的班级时旧行仍存在，直接 insert 会触发 DuplicateKeyException(500)。
        // 优先复用已有行（含 status=2/3 的历史记录），将其重新激活；否则再插入新行。
        ClassStudent existingTarget = classStudentMapper.selectOne(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, targetClassId)
                        .eq(ClassStudent::getStudentId, studentId)
                        .last("LIMIT 1"));
        if (existingTarget != null) {
            existingTarget.setStatus(1);
            existingTarget.setJoinTime(LocalDateTime.now());
            classStudentMapper.updateById(existingTarget);
        } else {
            ClassStudent newRecord = new ClassStudent();
            newRecord.setClassId(targetClassId);
            newRecord.setStudentId(studentId);
            newRecord.setStatus(1);
            newRecord.setJoinTime(LocalDateTime.now());
            try {
                classStudentMapper.insert(newRecord);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 软删除行仍占据物理唯一键时的兜底
                throw new BusinessException(409, "该学员在目标班级存在历史记录，无法重复添加");
            }
        }

        // M13 fix: 插入后再次校验容量，防止并发转班超出上限
        if (maxCount > 0) {
            Long newCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getClassId, targetClassId)
                            .eq(ClassStudent::getStatus, 1));
            if (newCount > maxCount) {
                throw new BusinessException(409, "目标班级已满，无法转入");
            }
        }

        // H fix: 同步该学员在源班级的活跃报名记录 classId 到目标班级。
        // detectTimeConflict 依据 Enrollment.classId 构建在班集合，若不同步，
        // 转班后仍检查旧班级（误报）且漏检新班级（漏报，可在新班级时段重复排课）。
        enrollmentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, studentId)
                .eq(Enrollment::getClassId, sourceRecord.getClassId())
                .in(Enrollment::getStatus, 1, 2, 3)
                .set(Enrollment::getClassId, targetClassId));

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

        // H2 fix: 退班后同步将关联的报名记录状态更新为 6（已退班/终止），
        // 避免报名记录仍显示为有效状态（待审核/待缴费/已完成）。
        // 仅更新非终态记录，已处于终态（4=已拒绝, 5=已失效, 6=已退班）的不重复处理。
        enrollmentMapper.update(null,
                new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .notIn(Enrollment::getStatus, List.of(4, 5, 6))
                        .set(Enrollment::getStatus, 6));

        // 4. 查找该学员所有缴费记录，按 enrollmentId 分组，为每个已缴费的报名生成退费申请
        // Critical fix: 原实现仅为最近一笔缴费创建退费，导致其他已缴费报名永久无法退费
        List<PaymentRecord> allPayments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .eq(PaymentRecord::getStudentId, studentId)
                        .isNotNull(PaymentRecord::getEnrollmentId));

        // 按 enrollmentId 去重（同一报名可能有多笔缴费，只需一张退费单）
        Set<Long> paidEnrollmentIds = allPayments.stream()
                .map(PaymentRecord::getEnrollmentId)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));

        List<Long> refundRecordIds = new ArrayList<>();
        if (!paidEnrollmentIds.isEmpty()) {
            com.pzhu.eduadmin.security.LoginUser operator = CurrentUserHolder.get();
            for (Long enrollmentId : paidEnrollmentIds) {
                // 取该报名下最近一笔缴费作为关联
                PaymentRecord payment = allPayments.stream()
                        .filter(p -> enrollmentId.equals(p.getEnrollmentId()))
                        .max(java.util.Comparator.comparing(PaymentRecord::getPayTime,
                                java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                        .orElse(null);
                RefundRecord refund = new RefundRecord();
                refund.setStudentId(studentId);
                refund.setApplicantId(operator != null ? operator.getUserId() : 0L);
                refund.setApplicantRole(operator != null ? operator.getRoleCode() : "SYSTEM");
                refund.setStatus(1); // 待审核
                refund.setAmount(BigDecimal.ZERO); // 退费金额由财务审核时确定
                refund.setLessonCount(BigDecimal.ZERO);
                refund.setPaymentRecordId(payment != null ? payment.getId() : null);
                refund.setEnrollmentId(enrollmentId);
                refundRecordMapper.insert(refund);
                refundRecordIds.add(refund.getId());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("studentId", studentId);
        result.put("withdrawnClassIds", currentRecords.stream().map(ClassStudent::getClassId).toList());
        result.put("refundRecordIds", refundRecordIds);
        result.put("message", !paidEnrollmentIds.isEmpty()
                ? "退班申请已提交，共生成 " + refundRecordIds.size() + " 笔退费单待财务审核"
                : "退班完成，该学员无缴费记录");
        return result;
    }

}
