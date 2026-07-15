package com.pzhu.eduadmin.modules.salary.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
                .collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, String> courseNames = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, Course::getName));
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
        salaryRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public SalaryRule updateSalaryRule(SalaryRule rule) {
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
                .collect(Collectors.toMap(User::getId, User::getRealName));
        for (TeacherSalary s : list) {
            s.setTeacherName(teacherNames.getOrDefault(s.getTeacherId(), ""));
        }
    }

    // ==================== 薪资自动核算 ====================

    @Override
    public TeacherSalary calculateSalary(String salaryMonth, Long teacherId, BigDecimal bonusAmount) {
        if (bonusAmount == null) bonusAmount = BigDecimal.ZERO;

        YearMonth ym = YearMonth.parse(salaryMonth);
        LocalDateTime calcSnapshot = LocalDateTime.now();
        LocalDateTime monthEnd = ym.atEndOfMonth().atTime(23, 59, 59);

        // 1. 查询该教师当月所有已完成课次（status=2 已完成）
        // 同时也要查代课课次（该课次 teacherId 不是该教师，但教师实际上了这堂课）
        List<ScheduleLesson> mainLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 2) // 已完成
                        .le(ScheduleLesson::getCreateTime, calcSnapshot)
                        .orderByAsc(ScheduleLesson::getLessonDate));

        // 2. 查询该教师的代课课次（通过考勤表找到非该教师但实际授课的课次）
        // 代课场景：课次 teacherId ≠ salary teacherId，但存在考勤记录
        // 简化方案：从已完成的非教师主讲课次中，查询是否存在考勤记录
        List<ScheduleLesson> allCompletedLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getStatus, 2)
                        .le(ScheduleLesson::getCreateTime, calcSnapshot));

        // 收集所有需要校验考勤的课次 ID
        Set<Long> allLessonIds = allCompletedLessons.stream()
                .map(ScheduleLesson::getId).collect(Collectors.toSet());

        // 批量查询所有课次的考勤记录，构建 lessonId -> 是否存在考勤的映射
        Map<Long, Long> lessonAttendanceMap = attendanceMapper.selectList(
                        new LambdaQueryWrapper<Attendance>()
                                .in(Attendance::getLessonId, allLessonIds)
                                .eq(Attendance::getStatus, 1)) // 到课才计入
                .stream()
                .collect(Collectors.groupingBy(Attendance::getLessonId, Collectors.counting()));

        // 5. 读取该教师全部薪资规则（按 教师+课程 区分），构建 courseId -> 规则 映射
        List<SalaryRule> rules = salaryRuleMapper.selectList(
                new LambdaQueryWrapper<SalaryRule>().eq(SalaryRule::getTeacherId, teacherId));
        if (rules == null || rules.isEmpty()) {
            throw new BusinessException(400, "该教师未配置薪资规则，请先设置课时单价");
        }
        SalaryRule defaultRule = rules.get(0);
        Map<Long, SalaryRule> courseRuleMap = rules.stream()
                .collect(Collectors.toMap(SalaryRule::getCourseId, r -> r, (a, b) -> a));

        // 解析 课次 -> 班级 -> 课程 的映射，以便按课程取对应课时单价
        Set<Long> classIds = new HashSet<>();
        mainLessons.forEach(l -> classIds.add(l.getClassId()));
        allCompletedLessons.forEach(l -> classIds.add(l.getClassId()));
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
        for (ScheduleLesson lesson : allCompletedLessons) {
            if (!lesson.getTeacherId().equals(teacherId) && lessonAttendanceMap.containsKey(lesson.getId())) {
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

        TeacherSalary salary = (existing != null) ? existing : new TeacherSalary();
        if (existing != null) {
            if (existing.getStatus() == 2) {
                throw new BusinessException(409, "该月薪资已确认，不可覆盖。请先作废后再重新核算");
            }
            salary = existing;
        }

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
        salary.setStatus(2);
        teacherSalaryMapper.updateById(salary);

        // 操作日志
        logOperation("薪资管理", "确认薪资(teacherId=" + salary.getTeacherId() + ",月份=" + salary.getSalaryMonth() + ",id=" + id + ")");

        return salary;
    }

    @Override
    public TeacherSalary voidSalary(Long id) {
        TeacherSalary salary = teacherSalaryMapper.selectById(id);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() == 4) throw new BusinessException(409, "薪资已撤销");
        salary.setStatus(4);
        teacherSalaryMapper.updateById(salary);

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
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
