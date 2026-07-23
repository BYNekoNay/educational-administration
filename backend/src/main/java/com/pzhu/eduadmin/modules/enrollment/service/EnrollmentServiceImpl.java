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
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import java.util.*;
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
    private final ScheduleLessonMapper scheduleLessonMapper;

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
        Set<Long> userIds = java.util.stream.Stream.concat(parentIds.stream(), auditorIds.stream()).collect(Collectors.toSet());
        // A2#6 fix: 空集合 selectBatchIds 生成非法 "IN ()"；值映射对 null 姓名降级
        Map<Long, String> userNames = userIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(userIds).stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName()
                                        : (u.getUsername() != null ? u.getUsername() : "用户" + u.getId()),
                                (a, b) -> a));
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
        com.pzhu.eduadmin.modules.student.entity.Student student =
                studentMapper.selectById(enrollment.getStudentId());
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }
        // Medium fix: 已退班学员（status=4）不允许再报名
        if (student.getStatus() != null && student.getStatus() == 4) {
            throw new BusinessException(409, "该学员已退班，无法报名");
        }
        // 校验课程是否存在且启用（A2#2 fix: 与_PARENT路径对齐，禁止报名已下架课程）
        Course course = courseMapper.selectById(enrollment.getCourseId());
        if (course == null) {
            throw new BusinessException(404, "课程不存在");
        }
        if (course.getStatus() == null || course.getStatus() != 1) {
            throw new BusinessException(409, "该课程已下架，无法报名");
        }
        // M19: 校验 classId 归属于 courseId
        if (enrollment.getClassId() != null) {
            ClassGroup classGroup = classGroupMapper.selectById(enrollment.getClassId());
            if (classGroup == null) {
                throw new BusinessException(404, "班级不存在");
            }
            if (!enrollment.getCourseId().equals(classGroup.getCourseId())) {
                throw new BusinessException(400, "该班级不属于所选课程");
            }
            // A2#2 fix: 与 transferStudent 对齐，仅允许报入开放班级
            if (classGroup.getStatus() == null || classGroup.getStatus() != 1) {
                throw new BusinessException(409, "该班级未开放，无法报名");
            }
        }

        // 时间冲突检测：检查学员已报名班级与目标班级是否存在排课时间重叠
        if (enrollment.getClassId() != null) {
            checkTimeConflict(enrollment.getStudentId(), enrollment.getClassId());
        }

        Long existCount = enrollmentMapper.selectCount(new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getStudentId, enrollment.getStudentId())
                .eq(Enrollment::getCourseId, enrollment.getCourseId())
                .notIn(Enrollment::getStatus, List.of(4, 5, 6)));  // M3 fix: 排除终态（已拒绝、已失效、已退费），其余均不可重复报名
        if (existCount > 0) {
            throw new BusinessException(409, "该学员已有此课程的报名记录（含待审核），不可重复报名");
        }

        // Bug #23 fix: 捕获唯一约束冲突，防止并发请求绕过重复检查（TOCTOU）
        try {
            enrollmentMapper.insert(enrollment);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该学员已有此课程的报名记录（含待审核），不可重复报名");
        }
        return enrollment;
    }

    @Override
    public Enrollment update(Enrollment enrollment) {
        enrollmentMapper.updateById(enrollment);
        return enrollmentMapper.selectById(enrollment.getId());
    }

    @Override
    public void updateClassId(Long id, Long classId) {
        // M2 fix: 仅更新 classId，避免 updateById 覆盖并发修改的其他字段
        enrollmentMapper.update(null, new LambdaUpdateWrapper<Enrollment>()
                .eq(Enrollment::getId, id)
                .set(Enrollment::getClassId, classId));
    }

    @Override
    public void validateClassBelongsToCourse(Long classId, Long courseId) {
        // M4 fix: 校验班级归属于课程
        ClassGroup classGroup = classGroupMapper.selectById(classId);
        if (classGroup == null) {
            throw new BusinessException(404, "班级不存在");
        }
        if (!courseId.equals(classGroup.getCourseId())) {
            throw new BusinessException(400, "该班级不属于所选课程");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(Long id) {
        // L8: 先加载报名记录并校验状态
        Enrollment enrollment = enrollmentMapper.selectById(id);
        if (enrollment == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        if (enrollment.getStatus() != null && (enrollment.getStatus() == 2 || enrollment.getStatus() == 3)) {
            throw new BusinessException(409, "已通过/在读的报名记录不可删除，请先办理退费或退班");
        }
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
        boolean deleted = enrollmentMapper.deleteById(id) > 0;
        if (deleted && enrollment.getClassId() != null) {
            // M fix: 置 status=3（已退出）而非逻辑删除，保留记录供流失统计（与 removeStudentFromClass 一致）。
            // deleteById/@TableLogic 会隐藏记录，导致按 status=3 计数的流失统计永远漏计。
            classStudentMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ClassStudent>()
                    .eq(ClassStudent::getClassId, enrollment.getClassId())
                    .eq(ClassStudent::getStudentId, enrollment.getStudentId())
                    .eq(ClassStudent::getStatus, 1)
                    .set(ClassStudent::getStatus, 3)
                    // update(null, wrapper) 不触发 MetaObjectHandler 自动填充，需显式刷新 update_time，
                    // 否则流失统计按 status=3 AND update_time 区间计数时会漏计本次退班。
                    .set(ClassStudent::getUpdateTime, LocalDateTime.now()));
        }
        if (deleted) {
            try {
                operationLogService.log("报名管理", "删除报名记录（学员=" + nameResolver.getStudentName(enrollment.getStudentId())
                        + "，id=" + id + "）");
            } catch (Exception e) {
                log.warn("操作日志记录失败: {}", e.getMessage());
            }
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
    public Page<Enrollment> pageByParentUserId(Long parentUserId, Long studentId, int pageNum, int pageSize) {
        LambdaQueryWrapper<Enrollment> wrapper = new LambdaQueryWrapper<Enrollment>()
                .eq(Enrollment::getParentUserId, parentUserId)
                .orderByDesc(Enrollment::getCreateTime);
        if (studentId != null) {
            wrapper.eq(Enrollment::getStudentId, studentId);
        }
        Page<Enrollment> page = enrollmentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
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
                .set(Enrollment::getAuditRemark, remark)
                // A2#3 fix: update(null,wrapper) 不触发自动填充，显式刷新 update_time
                .set(Enrollment::getUpdateTime, LocalDateTime.now());
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
        try {
            operationLogService.log("报名管理", status == 2
                    ? "审核通过报名（学员=" + studentName + "，课程=" + courseName + "，id=" + id + "）"
                    : "驳回报名（学员=" + studentName + "，课程=" + courseName + "，id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return enrollment;
    }

    /**
     * 定时任务：每分钟扫描待缴费状态且留位已过期的报名记录，自动标记为已失效(5)
     */
    @Scheduled(fixedRate = 60000)
    public void expirePendingEnrollments() {
        try {
            int updated = enrollmentMapper.update(null,
                    new LambdaUpdateWrapper<Enrollment>()
                            .eq(Enrollment::getStatus, 2)
                            .lt(Enrollment::getHoldExpireTime, LocalDateTime.now())
                            .set(Enrollment::getStatus, 5));
            if (updated > 0) {
                log.info("定时任务：过期报名记录 {} 条已标记为已失效", updated);
            }
        } catch (Exception e) {
            log.error("定时任务 expirePendingEnrollments 执行异常", e);
        }
    }

    // ======================== 时间冲突检测 ========================

    /**
     * 检测学员已报名班级与目标班级是否存在排课时间冲突（用于报名准入校验）。
     * 基于 schedule_lesson 的未来具体日期+时间段进行比对。
     * 若有冲突则直接抛 BusinessException(409)。
     */
    public void checkTimeConflict(Long studentId, Long targetClassId) {
        Map<String, Object> conflict = detectTimeConflict(studentId, targetClassId);
        if (conflict != null) {
            throw new BusinessException(409, (String) conflict.get("description"));
        }
    }

    /**
     * 检测时间冲突（返回详情 Map，供报名校验和前端预览共用）。
     * @return null=无冲突，否则返回 {description, conflictClassName, conflictDetail} 的 Map
     */
    public Map<String, Object> detectTimeConflict(Long studentId, Long targetClassId) {
        if (targetClassId == null) return null;

        // 1. 取学员所有活跃报名（排除终态：已拒绝/已失效/已退费）
        List<Enrollment> activeList = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>()
                        .eq(Enrollment::getStudentId, studentId)
                        .in(Enrollment::getStatus, List.of(1, 2, 3))
                        .isNotNull(Enrollment::getClassId));

        Set<Long> enrolledClassIds = activeList.stream()
                .map(Enrollment::getClassId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (enrolledClassIds.isEmpty()) return null;

        // 2. 把 targetClassId 也纳入批量查询
        Set<Long> allClassIds = new HashSet<>(enrolledClassIds);
        allClassIds.add(targetClassId);

        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusWeeks(8);

        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, allClassIds)
                        .eq(ScheduleLesson::getStatus, 1)
                        .between(ScheduleLesson::getLessonDate, today, horizon));

        // 3. 按 classId → lessonDate 分组
        Map<Long, Map<LocalDate, List<ScheduleLesson>>> grouped = lessons.stream()
                .collect(Collectors.groupingBy(ScheduleLesson::getClassId,
                        Collectors.groupingBy(ScheduleLesson::getLessonDate)));

        Map<Long, String> classNames = loadClassNames(allClassIds);

        // 目标班级的课次
        Map<LocalDate, List<ScheduleLesson>> targetSlots =
                grouped.getOrDefault(targetClassId, Collections.emptyMap());

        // 4. 逐一比对已报名班级
        for (Long enrolledClassId : enrolledClassIds) {
            // L1 fix: 跳过目标班级自身，避免学员"已报名该班级"时把自己的课次和自己比对，
            // overlap(同一时段) 恒为 true 会误报"自我冲突"
            if (enrolledClassId.equals(targetClassId)) continue;
            Map<LocalDate, List<ScheduleLesson>> existingSlots = grouped.get(enrolledClassId);
            if (existingSlots == null) continue;

            for (Map.Entry<LocalDate, List<ScheduleLesson>> targetEntry : targetSlots.entrySet()) {
                LocalDate date = targetEntry.getKey();
                List<ScheduleLesson> existingLessons = existingSlots.get(date);
                if (existingLessons == null) continue;

                for (ScheduleLesson ns : targetEntry.getValue()) {
                    for (ScheduleLesson es : existingLessons) {
                        if (overlap(ns.getStartTime(), ns.getEndTime(),
                                    es.getStartTime(), es.getEndTime())) {
                            String conflictName = classNames.getOrDefault(enrolledClassId, "未知班级");
                            String detail = dayOfWeekLabel(date.getDayOfWeek()) + " "
                                    + es.getStartTime() + "-" + es.getEndTime()
                                    + " 与 " + ns.getStartTime() + "-" + ns.getEndTime() + " 重叠";
                            String description = "该学员已报名[" + conflictName + "]，上课时间冲突：" + detail;
                            Map<String, Object> result = new HashMap<>();
                            result.put("description", description);
                            result.put("conflictClassName", conflictName);
                            result.put("conflictDetail", detail);
                            return result;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * 批量加载班级名称，无 classId 报名时跳过
     */
    private Map<Long, String> loadClassNames(Set<Long> classIds) {
        if (classIds.isEmpty()) return Collections.emptyMap();
        List<Map<String, Object>> raw = classGroupMapper.selectClassNamesByIdsIncludeDeleted(classIds);
        return raw.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> (String) m.get("class_name"),
                        (a, b) -> a));
    }

    /** 两个时间段是否重叠（不含端点相接，即 10:00 结束 vs 10:00 开始不算冲突） */
    private boolean overlap(LocalTime s1, LocalTime e1, LocalTime s2, LocalTime e2) {
        // 任一时间缺失（历史课次可能未填时间）时视为不冲突，避免 NPE 中断家长端冲突检测
        if (s1 == null || e1 == null || s2 == null || e2 == null) return false;
        return s1.isBefore(e2) && s2.isBefore(e1);
    }

    private static String dayOfWeekLabel(DayOfWeek dow) {
        return switch (dow) {
            case MONDAY -> "周一";
            case TUESDAY -> "周二";
            case WEDNESDAY -> "周三";
            case THURSDAY -> "周四";
            case FRIDAY -> "周五";
            case SATURDAY -> "周六";
            case SUNDAY -> "周日";
        };
    }
}
