package com.pzhu.eduadmin.modules.enrollment.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
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
    private final OperationLogMapper operationLogMapper;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;

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
        Set<Long> studentIds = list.stream().map(Enrollment::getStudentId).collect(Collectors.toSet());
        Set<Long> parentIds = list.stream().map(Enrollment::getParentUserId).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(Enrollment::getCourseId).collect(Collectors.toSet());
        Set<Long> classIds = list.stream().map(Enrollment::getClassId).filter(id -> id != null).collect(Collectors.toSet());
        Set<Long> auditorIds = list.stream().map(Enrollment::getAuditorId).filter(id -> id != null).collect(Collectors.toSet());

        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));
        Map<Long, String> userNames = userMapper.selectBatchIds(
                java.util.stream.Stream.concat(parentIds.stream(), auditorIds.stream()).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
        Map<Long, String> classNames = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroup::getId, ClassGroup::getClassName));

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
        logOperation("报名管理", "删除报名记录(id=" + id + ")");
        return enrollmentMapper.deleteById(id) > 0;
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
    public Enrollment audit(Long id, Integer status, Long auditorId, String remark) {
        Enrollment enrollment = enrollmentMapper.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (enrollment.getStatus() != 1) {
            throw new BusinessException(409, "非待审核状态的报名不可审核");
        }
        enrollment.setStatus(status);
        enrollment.setAuditorId(auditorId);
        enrollment.setAuditRemark(remark);
        // 审核通过：进入待缴费并设置留位截止时间（24小时）
        if (status == 2) {
            enrollment.setHoldExpireTime(LocalDateTime.now().plusHours(24));
        }
        enrollmentMapper.updateById(enrollment);

        // 操作日志
        logOperation("报名管理", status == 2 ? "审核通过报名(id=" + id + ")" : "驳回报名(id=" + id + ")");

        return enrollment;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
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
