package com.pzhu.eduadmin.modules.salary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.mapper.SalaryAdjustmentMapper;
import com.pzhu.eduadmin.modules.salary.mapper.SalaryRuleMapper;
import com.pzhu.eduadmin.modules.salary.mapper.TeacherSalaryMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalaryServiceImpl implements SalaryService {

    private final SalaryRuleMapper salaryRuleMapper;
    private final TeacherSalaryMapper teacherSalaryMapper;
    private final SalaryAdjustmentMapper salaryAdjustmentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final AttendanceMapper attendanceMapper;
    private final OperationLogMapper operationLogMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;

    private static final Map<String, SFunction<SalaryRule, ?>> RULE_SORT_MAP = Map.of("id", SalaryRule::getId);
    private static final Map<String, SFunction<TeacherSalary, ?>> SALARY_SORT_MAP = Map.of(
            "id", TeacherSalary::getId, "salaryMonth", TeacherSalary::getSalaryMonth,
            "totalAmount", TeacherSalary::getTotalAmount, "calcSnapshotTime", TeacherSalary::getCalcSnapshotTime
    );

    @Override
    public Page<SalaryRule> pageSalaryRules(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<SalaryRule> wrapper = new LambdaQueryWrapper<>();
        applyTeacherKeywordFilter(wrapper, keyword);
        QueryHelper.applySort(wrapper, sortField, sortOrder, RULE_SORT_MAP, () -> wrapper.orderByDesc(SalaryRule::getId));
        Page<SalaryRule> page = salaryRuleMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateSalaryRuleNames(page.getRecords());
        return page;
    }

    private void applyTeacherKeywordFilter(LambdaQueryWrapper<SalaryRule> wrapper, String keyword) {
        Set<Long> teacherIds = findTeacherIdsByName(keyword);
        if (teacherIds != null) {
            wrapper.in(SalaryRule::getTeacherId, teacherIds);
        }
    }

    private void populateSalaryRuleNames(List<SalaryRule> list) {
        if (list.isEmpty()) return;
        Set<Long> teacherIds = list.stream().map(SalaryRule::getTeacherId).collect(Collectors.toSet());
        Set<Long> courseIds = list.stream().map(SalaryRule::getCourseId).collect(Collectors.toSet());
        Map<Long, String> teacherNames = userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
        for (SalaryRule r : list) {
            r.setTeacherName(teacherNames.getOrDefault(r.getTeacherId(), ""));
            r.setCourseName(courseNames.getOrDefault(r.getCourseId(), ""));
        }
    }

    @Override
    public SalaryRule createSalaryRule(SalaryRule rule) {
        if (rule.getLessonUnitPrice() == null || rule.getLessonUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "课时单价必须大于0");
        }
        if (rule.getSubstituteRate() == null) rule.setSubstituteRate(BigDecimal.ONE);
        // Issue #19: 检查教师+课程唯一性
        Long existCount = salaryRuleMapper.selectCount(
                new LambdaQueryWrapper<SalaryRule>()
                        .eq(SalaryRule::getTeacherId, rule.getTeacherId())
                        .eq(SalaryRule::getCourseId, rule.getCourseId()));
        if (existCount > 0) {
            throw new BusinessException(409, "该教师在此课程已有薪资规则，不可重复创建");
        }
        salaryRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public SalaryRule updateSalaryRule(SalaryRule rule) {
        SalaryRule existing = salaryRuleMapper.selectById(rule.getId());
        if (existing == null) {
            throw new BusinessException(404, "薪资规则不存在");
        }
        if (rule.getLessonUnitPrice() != null && rule.getLessonUnitPrice().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "课时单价必须大于0");
        }
        salaryRuleMapper.updateById(rule);
        return salaryRuleMapper.selectById(rule.getId());
    }

    @Override
    public void deleteSalaryRule(Long id) {
        salaryRuleMapper.deleteById(id);
    }

    @Override
    public Page<TeacherSalary> pageTeacherSalaries(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<TeacherSalary> wrapper = new LambdaQueryWrapper<>();
        Set<Long> teacherIds = findTeacherIdsByName(keyword);
        if (teacherIds != null) {
            wrapper.in(TeacherSalary::getTeacherId, teacherIds);
        }
        QueryHelper.applySort(wrapper, sortField, sortOrder, SALARY_SORT_MAP, () -> wrapper.orderByDesc(TeacherSalary::getSalaryMonth));
        Page<TeacherSalary> page = teacherSalaryMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateTeacherSalaryNames(page.getRecords());
        return page;
    }

    /**
     * 按教师姓名模糊匹配用户表，返回匹配的教师 userId 集合。
     * 返回 null 表示未传 keyword（不加过滤）；返回空集合则不应有匹配记录。
     */
    private Set<Long> findTeacherIdsByName(String keyword) {
        if (!StringUtils.hasText(keyword)) return null;
        List<User> matched = userMapper.selectList(
                new LambdaQueryWrapper<User>().like(User::getRealName, keyword));
        if (matched.isEmpty()) return Collections.emptySet();
        return matched.stream().map(User::getId).collect(Collectors.toSet());
    }

    private void populateTeacherSalaryNames(List<TeacherSalary> list) {
        if (list.isEmpty()) return;
        Set<Long> teacherIds = list.stream().map(TeacherSalary::getTeacherId).collect(Collectors.toSet());
        Map<Long, String> teacherNames = userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId,
                        u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(),
                        (a, b) -> a));
        for (TeacherSalary s : list) {
            s.setTeacherName(teacherNames.getOrDefault(s.getTeacherId(), ""));
        }
    }

    // ==================== 薪资自动核算 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TeacherSalary calculateSalary(String salaryMonth, Long teacherId, BigDecimal bonusAmount) {
        if (bonusAmount == null) bonusAmount = BigDecimal.ZERO;

        YearMonth ym = YearMonth.parse(salaryMonth);
        LocalDateTime calcSnapshot = LocalDateTime.now();

        // 核算月份日期范围
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // 1. 查询该教师当月所有已完成课次（status=2 已完成），排除代课课次（代课单独核算）
        List<ScheduleLesson> mainLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 2) // 已完成
                        .ge(ScheduleLesson::getLessonDate, monthStart)
                        .le(ScheduleLesson::getLessonDate, monthEnd)
                        .isNull(ScheduleLesson::getSourceLessonId) // 排除代课课次，避免双重计算
                        .orderByAsc(ScheduleLesson::getLessonDate));

        // 2. 查询该教师当月所有代课课次（sourceLessonId 不为空表示是代课）
        List<ScheduleLesson> substituteLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 2) // 已完成
                        .ge(ScheduleLesson::getLessonDate, monthStart)
                        .le(ScheduleLesson::getLessonDate, monthEnd)
                        .isNotNull(ScheduleLesson::getSourceLessonId));

        // 收集所有需要校验考勤的课次 ID（主讲 + 代课）
        Set<Long> allLessonIds = new HashSet<>();
        mainLessons.forEach(l -> allLessonIds.add(l.getId()));
        substituteLessons.forEach(l -> allLessonIds.add(l.getId()));

        // 批量查询所有课次的考勤记录，构建 lessonId -> 是否存在考勤的映射
        Map<Long, Long> lessonAttendanceMap;
        if (!allLessonIds.isEmpty()) {
            lessonAttendanceMap = attendanceMapper.selectList(
                            new LambdaQueryWrapper<Attendance>()
                                    .in(Attendance::getLessonId, allLessonIds)
                                    .in(Attendance::getStatus, 1, 2)) // 到课/迟到均算授课
                    .stream()
                    .collect(Collectors.groupingBy(Attendance::getLessonId, Collectors.counting()));
        } else {
            lessonAttendanceMap = Collections.emptyMap();
        }

        // 5. 读取该教师全部薪资规则，按创建时间排序，取第一条作为兜底
        List<SalaryRule> rules = salaryRuleMapper.selectList(
                new LambdaQueryWrapper<SalaryRule>()
                        .eq(SalaryRule::getTeacherId, teacherId)
                        .orderByAsc(SalaryRule::getCreateTime));
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(400, "该教师未配置薪资规则，请先设置课时单价");
        }
        SalaryRule defaultRule = rules.get(0);
        Map<Long, SalaryRule> courseRuleMap = rules.stream()
                .collect(Collectors.toMap(SalaryRule::getCourseId, r -> r, (a, b) -> a));

        // 解析 课次 -> 班级 -> 课程 的映射，以便按课程取对应课时单价
        Set<Long> classIds = new HashSet<>();
        mainLessons.forEach(l -> classIds.add(l.getClassId()));
        substituteLessons.forEach(l -> classIds.add(l.getClassId()));
        Map<Long, Long> classCourseMap;
        if (!classIds.isEmpty()) {
            classCourseMap = classGroupMapper.selectList(
                            new LambdaQueryWrapper<ClassGroup>().in(ClassGroup::getId, classIds))
                    .stream()
                    .collect(Collectors.toMap(ClassGroup::getId, ClassGroup::getCourseId, (a, b) -> a));
        } else {
            classCourseMap = Map.of();
        }

        // 解析某课次应采用的薪资规则：按班级所属课程匹配，无匹配则兜底用首条规则
        Function<Long, SalaryRule> resolveRule = classId -> {
            Long courseId = classCourseMap.get(classId);
            SalaryRule r = courseId != null ? courseRuleMap.get(courseId) : null;
            return r != null ? r : defaultRule;
        };

        // 3. 统计主讲课时并按下课单价累加
        BigDecimal mainLessonCount = BigDecimal.ZERO;
        BigDecimal baseAmount = BigDecimal.ZERO;
        for (ScheduleLesson lesson : mainLessons) {
            if (lessonAttendanceMap.containsKey(lesson.getId())) {
                mainLessonCount = mainLessonCount.add(BigDecimal.ONE);
                SalaryRule r = resolveRule.apply(lesson.getClassId());
                baseAmount = baseAmount.add(r.getLessonUnitPrice());
            }
        }

        // 4. 统计代课课时并按下课单价×代课系数累加
        BigDecimal substituteCount = BigDecimal.ZERO;
        BigDecimal substituteAmount = BigDecimal.ZERO;
        for (ScheduleLesson lesson : substituteLessons) {
            if (lessonAttendanceMap.containsKey(lesson.getId())) {
                substituteCount = substituteCount.add(BigDecimal.ONE);
                SalaryRule r = resolveRule.apply(lesson.getClassId());
                BigDecimal unitPrice = r.getLessonUnitPrice();
                BigDecimal rate = r.getSubstituteRate() != null ? r.getSubstituteRate() : BigDecimal.ONE;
                substituteAmount = substituteAmount.add(unitPrice.multiply(rate));
            }
        }

        // 6. 计算金额
        baseAmount = baseAmount.setScale(2, RoundingMode.HALF_UP);
        substituteAmount = substituteAmount.setScale(2, RoundingMode.HALF_UP);
        BigDecimal totalAmount = baseAmount.add(substituteAmount).add(bonusAmount).setScale(2, RoundingMode.HALF_UP);

        // 7. 检查是否已存在薪资单
        TeacherSalary existing = teacherSalaryMapper.selectOne(new LambdaQueryWrapper<TeacherSalary>()
                .eq(TeacherSalary::getTeacherId, teacherId)
                .eq(TeacherSalary::getSalaryMonth, salaryMonth));

        if (existing != null) {
            if (existing.getStatus() == 2) {
                throw new BusinessException(409, "该月薪资已确认，不可覆盖。请先作废后再重新核算");
            }
        }
        TeacherSalary salary = (existing != null) ? existing : new TeacherSalary();

        salary.setTeacherId(teacherId);
        salary.setSalaryMonth(salaryMonth);
        salary.setLessonCount(mainLessonCount);
        salary.setSubstituteCount(substituteCount);
        salary.setBaseAmount(baseAmount);
        salary.setBonusAmount(bonusAmount);
        salary.setTotalAmount(totalAmount);
        salary.setStatus(1); // 待确认
        salary.setCalcSnapshotTime(calcSnapshot);

        if (existing != null) {
            teacherSalaryMapper.updateById(salary);
        } else {
            teacherSalaryMapper.insert(salary);
        }
        return salary;
    }

    @Override
    public TeacherSalary confirmSalary(Long id) {
        TeacherSalary salary = teacherSalaryMapper.selectById(id);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() == 2) throw new BusinessException(409, "薪资已确认");
        if (salary.getStatus() == 4) throw new BusinessException(409, "已撤销的薪资不可确认，请重新核算");

        // CAS 原子更新：防止并发确认
        int updated = teacherSalaryMapper.update(null,
                new LambdaUpdateWrapper<TeacherSalary>()
                        .eq(TeacherSalary::getId, id)
                        .eq(TeacherSalary::getStatus, salary.getStatus())
                        .set(TeacherSalary::getStatus, 2));
        if (updated == 0) {
            throw new BusinessException(409, "薪资状态已变更，请刷新后重试");
        }
        salary.setStatus(2);

        // 操作日志
        logOperation("薪资管理", "确认薪资(teacherId=" + salary.getTeacherId() + ",月份=" + salary.getSalaryMonth() + ",id=" + id + ")");

        return salary;
    }

    @Override
    public TeacherSalary voidSalary(Long id) {
        TeacherSalary salary = teacherSalaryMapper.selectById(id);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() == 4) throw new BusinessException(409, "薪资已撤销");

        // CAS 原子更新：防止并发作废
        int updated = teacherSalaryMapper.update(null,
                new LambdaUpdateWrapper<TeacherSalary>()
                        .eq(TeacherSalary::getId, id)
                        .eq(TeacherSalary::getStatus, salary.getStatus())
                        .set(TeacherSalary::getStatus, 4));
        if (updated == 0) {
            throw new BusinessException(409, "薪资状态已变更，请刷新后重试");
        }
        salary.setStatus(4);

        // 操作日志
        logOperation("薪资管理", "撤销薪资(teacherId=" + salary.getTeacherId()
                + ",月份=" + salary.getSalaryMonth() + ",id=" + id + ")");

        return salary;
    }

    @Override
    public SalaryAdjustment createAdjustment(Long salaryId, BigDecimal adjustAmount, String reason, Long operatorId) {
        TeacherSalary salary = teacherSalaryMapper.selectById(salaryId);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() != 2) throw new BusinessException(409, "仅可对已确认的薪资进行调整");
        if (adjustAmount == null || adjustAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(400, "调整金额不能为空或为零");
        }

        SalaryAdjustment adj = new SalaryAdjustment();
        adj.setTeacherSalaryId(salaryId);
        adj.setAdjustAmount(adjustAmount);
        adj.setReason(reason);
        adj.setOperatorId(operatorId);
        salaryAdjustmentMapper.insert(adj);
        return adj;
    }

    @Override
    public Page<SalaryAdjustment> pageSalaryAdjustments(int pageNum, int pageSize) {
        return salaryAdjustmentMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser operator = CurrentUserHolder.get();
        log.setOperatorId(operator != null ? operator.getUserId() : 0L);
        log.setModule(module);
        log.setOperation(operation);
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);
    }
}
