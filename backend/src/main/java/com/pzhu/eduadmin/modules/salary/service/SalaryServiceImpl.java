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
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalaryServiceImpl implements SalaryService {

    private final SalaryRuleMapper salaryRuleMapper;
    private final TeacherSalaryMapper teacherSalaryMapper;
    private final SalaryAdjustmentMapper salaryAdjustmentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final AttendanceMapper attendanceMapper;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
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
            // A5#1 fix: 空集合会生成非法 "teacher_id IN ()"，用 -1 哨兵确保合法地返回空结果
            wrapper.in(SalaryRule::getTeacherId, teacherIds.isEmpty() ? Set.of(-1L) : teacherIds);
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
        if (rule.getSubstituteRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "代课系数必须大于0");
        }
        // Issue #19: 检查教师+课程唯一性
        Long existCount = salaryRuleMapper.selectCount(
                new LambdaQueryWrapper<SalaryRule>()
                        .eq(SalaryRule::getTeacherId, rule.getTeacherId())
                        .eq(SalaryRule::getCourseId, rule.getCourseId()));
        if (existCount > 0) {
            throw new BusinessException(409, "该教师在此课程已有薪资规则，不可重复创建");
        }
        // H6 fix: 软删除行仍占用物理唯一键，捕获 DuplicateKeyException
        try {
            salaryRuleMapper.insert(rule);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "该教师在此课程已有薪资规则（含已删除），不可重复创建");
        }
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
        if (rule.getSubstituteRate() != null && rule.getSubstituteRate().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "代课系数必须大于0");
        }
        // M24: 禁止修改 teacherId/courseId，防止历史薪资重算使用错误费率
        rule.setTeacherId(null);
        rule.setCourseId(null);
        salaryRuleMapper.updateById(rule);
        return salaryRuleMapper.selectById(rule.getId());
    }

    @Override
    public void deleteSalaryRule(Long id) {
        salaryRuleMapper.deleteById(id);
    }

    @Override
    public Page<TeacherSalary> pageTeacherSalaries(int pageNum, int pageSize, String keyword, Integer status, String month, String sortField, String sortOrder) {
        LambdaQueryWrapper<TeacherSalary> wrapper = new LambdaQueryWrapper<>();
        Set<Long> teacherIds = findTeacherIdsByName(keyword);
        if (teacherIds != null) {
            // A5#1 fix: 空集合会生成非法 "teacher_id IN ()"，用 -1 哨兵确保合法地返回空结果
            wrapper.in(TeacherSalary::getTeacherId, teacherIds.isEmpty() ? Set.of(-1L) : teacherIds);
        }
        if (status != null) {
            wrapper.eq(TeacherSalary::getStatus, status);
        }
        if (month != null && !month.isBlank()) {
            wrapper.eq(TeacherSalary::getSalaryMonth, month);
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

        YearMonth ym;
        try {
            ym = YearMonth.parse(salaryMonth);
        } catch (DateTimeParseException e) {
            throw new BusinessException(400, "salaryMonth格式不正确，应为yyyy-MM");
        }
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
            // A5#4 fix: 班级可能已软删，selectList 遵循 @TableLogic 会漏掉历史课次的班级 → 误用默认规则算错薪资。
            // 逐个用绕过逻辑删除的 selectCourseIdByIdIncludeDeleted 解析 class→course
            classCourseMap = new java.util.HashMap<>();
            for (Long cid : classIds) {
                if (cid == null) continue;
                Long courseId = classGroupMapper.selectCourseIdByIdIncludeDeleted(cid);
                if (courseId != null) classCourseMap.put(cid, courseId);
            }
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
        // H2 fix: salary 是 existing 的别名，下方 setStatus(1) 会改写同一对象，
        // 必须在改写前先捕获原始状态，否则 CAS 条件恒为 status=1，
        // 导致"作废(status=4)后重新核算"流程必然 409 卡死。
        Integer originalStatus = existing != null ? existing.getStatus() : null;
        TeacherSalary salary = (existing != null) ? existing : new TeacherSalary();

        salary.setTeacherId(teacherId);
        salary.setSalaryMonth(salaryMonth);
        salary.setLessonCount(mainLessonCount);
        salary.setSubstituteCount(substituteCount);
        salary.setBaseAmount(baseAmount);
        salary.setSubstituteAmount(substituteAmount); // Bug #32 fix: 代课金额单独持久化
        salary.setBonusAmount(bonusAmount);
        salary.setTotalAmount(totalAmount);
        salary.setStatus(1); // 待确认
        salary.setCalcSnapshotTime(calcSnapshot);

        if (existing != null) {
            // M1 fix: 条件更新（CAS on status），防止并发确认(1→2)/作废(→4)被核算写回覆盖。
            // 仅当状态仍等于核算开始时读到的状态才允许写回，否则说明已被其他操作改变。
            LambdaUpdateWrapper<TeacherSalary> updateWrapper = new LambdaUpdateWrapper<TeacherSalary>()
                    .eq(TeacherSalary::getId, existing.getId())
                    .eq(originalStatus != null, TeacherSalary::getStatus, originalStatus);
            int updated = teacherSalaryMapper.update(salary, updateWrapper);
            if (updated == 0) {
                throw new BusinessException(409, "薪资状态已变更（可能已被确认或作废），请刷新后重试");
            }
        } else {
            try {
                teacherSalaryMapper.insert(salary);
            } catch (DuplicateKeyException e) {
                throw new BusinessException(409, "该月薪资正在被其他操作核算，请稍后重试");
            }
        }
        return salary;
    }

    @Override
    public Map<String, Object> calculateBatchSalary(String salaryMonth, BigDecimal bonusAmount) {
        if (!StringUtils.hasText(salaryMonth)) {
            throw new BusinessException(400, "salaryMonth不能为空");
        }
        // 拉取所有在职（status=1）TEACHER 角色的用户
        List<User> teachers = userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRoleCode, "TEACHER")
                        .eq(User::getStatus, 1)
                        .orderByAsc(User::getId));
        if (teachers.isEmpty()) {
            throw new BusinessException(404, "暂无在职教师，无需批量结算");
        }

        int successCount = 0, failedCount = 0;
        BigDecimal totalAmount = BigDecimal.ZERO;
        List<Map<String, Object>> errors = new ArrayList<>();
        List<Map<String, Object>> successes = new ArrayList<>();

        for (User teacher : teachers) {
            try {
                TeacherSalary salary = calculateSalary(salaryMonth, teacher.getId(), bonusAmount != null ? bonusAmount : BigDecimal.ZERO);
                successCount++;
                if (salary.getTotalAmount() != null) {
                    totalAmount = totalAmount.add(salary.getTotalAmount());
                }
                Map<String, Object> ok = new LinkedHashMap<>();
                ok.put("teacherId", teacher.getId());
                ok.put("teacherName", teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername());
                ok.put("totalAmount", salary.getTotalAmount());
                ok.put("lessonCount", salary.getLessonCount());
                ok.put("substituteCount", salary.getSubstituteCount());
                successes.add(ok);
            } catch (Exception e) {
                // 单教师失败不阻断整体：仅记录明细、计入失败数
                failedCount++;
                Map<String, Object> err = new LinkedHashMap<>();
                err.put("teacherId", teacher.getId());
                err.put("teacherName", teacher.getRealName() != null ? teacher.getRealName() : teacher.getUsername());
                err.put("message", e.getMessage() != null ? e.getMessage() : "未知错误");
                errors.add(err);
                log.warn("[batch salary] teacher={} month={} failed: {}", teacher.getId(), salaryMonth, e.getMessage());
            }
        }

        operationLogService.log("薪资管理", String.format("一键结算（月份=%s，成功=%d，失败=%d，合计=¥%s）",
                salaryMonth, successCount, failedCount, totalAmount.setScale(2, RoundingMode.HALF_UP)));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("salaryMonth", salaryMonth);
        result.put("totalTeachers", teachers.size());
        result.put("successCount", successCount);
        result.put("failedCount", failedCount);
        result.put("totalAmount", totalAmount.setScale(2, RoundingMode.HALF_UP));
        result.put("successes", successes);
        result.put("errors", errors);
        return result;
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
        try {
            operationLogService.log("薪资管理", "确认薪资（教师=" + nameResolver.getUserDisplayName(salary.getTeacherId())
                    + "，月份=" + salary.getSalaryMonth() + "，金额=" + salary.getTotalAmount() + "，id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

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
        try {
            operationLogService.log("薪资管理", "撤销薪资（教师=" + nameResolver.getUserDisplayName(salary.getTeacherId())
                    + "，月份=" + salary.getSalaryMonth() + "，id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return salary;
    }

    @Override
    public TeacherSalary paySalary(Long id) {
        TeacherSalary salary = teacherSalaryMapper.selectById(id);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() == 3) throw new BusinessException(409, "薪资已发放");
        if (salary.getStatus() != 2) throw new BusinessException(409, "仅已确认的薪资可发放");

        // CAS 原子更新：防止并发发放
        int updated = teacherSalaryMapper.update(null,
                new LambdaUpdateWrapper<TeacherSalary>()
                        .eq(TeacherSalary::getId, id)
                        .eq(TeacherSalary::getStatus, 2)
                        .set(TeacherSalary::getStatus, 3));
        if (updated == 0) {
            throw new BusinessException(409, "薪资状态已变更，请刷新后重试");
        }
        salary.setStatus(3);

        // 操作日志
        try {
            operationLogService.log("薪资管理", "发放薪资（教师=" + nameResolver.getUserDisplayName(salary.getTeacherId())
                    + "，月份=" + salary.getSalaryMonth() + "，金额=" + salary.getTotalAmount() + "，id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return salary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SalaryAdjustment createAdjustment(Long salaryId, BigDecimal adjustAmount, String reason, Long operatorId) {
        TeacherSalary salary = teacherSalaryMapper.selectById(salaryId);
        if (salary == null) throw new BusinessException(404, "薪资记录不存在");
        if (salary.getStatus() != 2) throw new BusinessException(409, "仅可对已确认的薪资进行调整");
        if (adjustAmount == null || adjustAmount.compareTo(BigDecimal.ZERO) == 0) {
            throw new BusinessException(400, "调整金额不能为空或为零");
        }
        // L fix: 允许负向调整（更正）但禁止把薪资总额减为负数
        if (salary.getTotalAmount() != null
                && salary.getTotalAmount().add(adjustAmount).compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(400, "调整金额过大会导致薪资总额为负，请核对");
        }

        SalaryAdjustment adj = new SalaryAdjustment();
        adj.setTeacherSalaryId(salaryId);
        adj.setAdjustAmount(adjustAmount);
        adj.setReason(reason);
        adj.setOperatorId(operatorId);
        salaryAdjustmentMapper.insert(adj);

        // H12 fix: 原子 SQL 递增 totalAmount，防止并发调整丢失更新
        // （原 read-sum-write 在 REPEATABLE_READ 下并发时 SUM 仅见自身插入）
        // Medium fix: 更新条件附带 status=2，防止并发 voidSalary 后调整落到已作废薪资上
        int updated = teacherSalaryMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<TeacherSalary>()
                        .eq(TeacherSalary::getId, salaryId)
                        .eq(TeacherSalary::getStatus, 2)
                        .setSql("total_amount = total_amount + " + adjustAmount.toPlainString()));
        if (updated == 0) {
            throw new BusinessException(409, "薪资状态已变更（可能已被作废），调整失败");
        }

        return adj;
    }

    @Override
    public Page<SalaryAdjustment> pageSalaryAdjustments(int pageNum, int pageSize) {
        return salaryAdjustmentMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }
}
