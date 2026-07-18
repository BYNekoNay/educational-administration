package com.pzhu.eduadmin.modules.enrollment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrollmentServiceImpl implements EnrollmentService {

    private final EnrollmentMapper enrollmentMapper;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;

    private static final Map<String, SFunction<Enrollment, ?>> ENROLLMENT_SORT_MAP = Map.of(
            "id", Enrollment::getId,
            "createTime", Enrollment::getCreateTime,
            "status", Enrollment::getStatus
    );

    @Override
    public Page<Enrollment> page(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, ENROLLMENT_SORT_MAP, () -> wrapper.orderByDesc(Enrollment::getCreateTime));
        Page<Enrollment> page = enrollmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateNames(page.getRecords());
        return page;
    }

    /** 填充报名记录中的关联名称字段 */
    private void populateNames(List<Enrollment> list) {
        if (list.isEmpty()) return;
        // 收集所有需要查询的 ID
        Set<Long> studentIds = list.stream().map(Enrollment::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> parentIds = list.stream().map(Enrollment::getParentUserId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(Enrollment::getCourseId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> classIds = list.stream().map(Enrollment::getClassId).filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> auditorIds = list.stream().map(Enrollment::getAuditorId).filter(id -> id != null).collect(Collectors.toSet());

        // 历史报名可能引用已软删学员，绕过 @TableLogic 取名
        Map<Long, String> studentNames;
        if (studentIds.isEmpty()) {
            studentNames = Collections.emptyMap();
        } else {
            List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(studentIds);
            studentNames = raw.stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("name"),
                            (a, b) -> a));
        }
        Map<Long, String> userNames = userMapper.selectBatchIds(
                java.util.stream.Stream.concat(parentIds.stream(), auditorIds.stream()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(), (a, b) -> a));
        // 历史报名可能引用已软删课程/班级，绕过 @TableLogic 取名
        Map<Long, String> courseNames;
        if (courseIds.isEmpty()) {
            courseNames = Collections.emptyMap();
        } else {
            List<Map<String, Object>> rawCourse = courseMapper.selectNamesByIdsIncludeDeleted(courseIds);
            courseNames = rawCourse.stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("name"),
                            (a, b) -> a));
        }
        Map<Long, String> classNames;
        if (classIds.isEmpty()) {
            classNames = Collections.emptyMap();
        } else {
            List<Map<String, Object>> rawClass = classGroupMapper.selectClassNamesByIdsIncludeDeleted(classIds);
            classNames = rawClass.stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("class_name"),
                            (a, b) -> a));
        }

        for (Enrollment e : list) {
            e.setStudentName(studentNames.getOrDefault(e.getStudentId(), ""));
            e.setParentName(userNames.getOrDefault(e.getParentUserId(), ""));
            e.setCourseName(courseNames.getOrDefault(e.getCourseId(), ""));
            e.setClassName(classNames.getOrDefault(e.getClassId(), ""));
            e.setAuditorName(e.getAuditorId() != null ? userNames.getOrDefault(e.getAuditorId(), "") : "");
        }
    }

    @Override
    public Enrollment getById(Long id) {
        return enrollmentMapper.selectById(id);
    }

    @Override
    public Enrollment create(Enrollment enrollment) {
        if (enrollment.getStudentId() == null) throw new BusinessException(400, "学员ID不能为空");
        if (enrollment.getCourseId() == null) throw new BusinessException(400, "课程ID不能为空");

        // 校验学员是否存在
        if (studentMapper.selectById(enrollment.getStudentId()) == null) {
            throw new BusinessException(404, "学员不存在");
        }
        // 校验课程是否存在
        if (courseMapper.selectById(enrollment.getCourseId()) == null) {
            throw new BusinessException(404, "课程不存在");
        }

        Long existCount = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, enrollment.getStudentId())
                .eq(Enrollment::getCourseId, enrollment.getCourseId())
                .notIn(Enrollment::getStatus, List.of(4, 5)));  // 排除终态（已拒绝、已失效），其余均不可重复报名
        if (existCount > 0) {
            throw new BusinessException(409, "该学员已有此课程的报名记录（含待审核），不可重复报名");
        }

        enrollmentMapper.insert(enrollment);
        return enrollment;
    }

    @Override
    public Enrollment update(Enrollment enrollment) {
        enrollmentMapper.updateById(enrollment);
        return enrollmentMapper.selectById(enrollment.getId());
    }

    @Override
    public boolean delete(Long id) {
        // Issue #14: 检查是否有关联的财务记录
        Long paymentCount = paymentRecordMapper.selectCount(
                new LambdaQueryWrapper<PaymentRecord>().eq(PaymentRecord::getEnrollmentId, id));
        if (paymentCount > 0) {
            throw new BusinessException(409, "该报名已有缴费记录，无法删除");
        }
        Long refundCount = refundRecordMapper.selectCount(
                new LambdaQueryWrapper<RefundRecord>().eq(RefundRecord::getEnrollmentId, id));
        if (refundCount > 0) {
            throw new BusinessException(409, "该报名已有退费记录，无法删除");
        }
        Enrollment enrollment = enrollmentMapper.selectById(id);
        boolean deleted = enrollmentMapper.deleteById(id) > 0;
        if (deleted && enrollment != null && enrollment.getClassId() != null) {
            // 清理关联的 ClassStudent 记录
            classStudentMapper.delete(new LambdaQueryWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, enrollment.getClassId())
                    .eq(ClassStudent::getStudentId, enrollment.getStudentId()));
        }
        if (deleted) {
            operationLogService.log("报名管理", "删除报名记录（学员=" + nameResolver.getStudentName(enrollment.getStudentId())
                    + "，id=" + id + "）");
        }
        return deleted;
    }

    @Override
    public Page<Enrollment> pageByParentUserId(Long parentUserId, int pageNum, int pageSize) {
        Page<Enrollment> page = enrollmentMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Enrollment>().eq(Enrollment::getParentUserId, parentUserId)
                        .orderByDesc(Enrollment::getCreateTime));
        populateNames(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Enrollment audit(Long id, Integer status, Long auditorId, String remark) {
        Enrollment enrollment = enrollmentMapper.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (enrollment.getStatus() != 1) {
            throw new BusinessException(409, "非待审核状态的报名不可审核");
        }
        if (status != 2 && status != 4) {
            throw new BusinessException(400, "无效的审核状态，仅支持 2-通过 或 4-驳回");
        }
        // CAS 原子更新：防止并发审核
        LambdaUpdateWrapper<Enrollment> updateWrapper = new LambdaUpdateWrapper<Enrollment>()
                .eq(Enrollment::getId, id)
                .eq(Enrollment::getStatus, 1)
                .set(Enrollment::getStatus, status)
                .set(Enrollment::getAuditorId, auditorId)
                .set(Enrollment::getAuditRemark, remark);
        if (status == 2) {
            updateWrapper.set(Enrollment::getHoldExpireTime, LocalDateTime.now().plusHours(24));
        }
        int updated = enrollmentMapper.update(null, updateWrapper);
        if (updated == 0) {
            throw new BusinessException(409, "报名状态已变更，请刷新后重试");
        }
        enrollment.setStatus(status);
        enrollment.setAuditorId(auditorId);
        enrollment.setAuditRemark(remark);
        if (status == 2) {
            enrollment.setHoldExpireTime(LocalDateTime.now().plusHours(24));
        }

        // 操作日志
        String studentName = nameResolver.getStudentName(enrollment.getStudentId());
        String courseName = nameResolver.getCourseName(enrollment.getCourseId());
        operationLogService.log("报名管理", status == 2
                ? "审核通过报名（学员=" + studentName + "，课程=" + courseName + "，id=" + id + "）"
                : "驳回报名（学员=" + studentName + "，课程=" + courseName + "，id=" + id + "）");

        return enrollment;
    }

    /**
     * 定时任务：每分钟扫描待缴费状态且留位已过期的报名记录，自动标记为已失效(5)
     */
    @Scheduled(fixedRate = 60000)
    public void expirePendingEnrollments() {
        int updated = enrollmentMapper.update(null,
                new LambdaUpdateWrapper<Enrollment>()
                        .eq(Enrollment::getStatus, 2)
                        .lt(Enrollment::getHoldExpireTime, LocalDateTime.now())
                        .set(Enrollment::getStatus, 5));
        if (updated > 0) {
            log.info("定时任务：过期报名记录 {} 条已标记为已失效", updated);
        }
    }
}
