package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OrganizationMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.StatisticsSnapshotMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final StatisticsSnapshotMapper statisticsSnapshotMapper;
    private final OrganizationMapper organizationMapper;
    private final OperationLogMapper operationLogMapper;
    private final OperationLogService operationLogService;
    private final UserMapper userMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final AttendanceMapper attendanceMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final PaymentRecordMapper paymentRecordMapper;
    private final RefundRecordMapper refundRecordMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CourseMapper courseMapper;

    @Override
    public Page<StatisticsSnapshot> pageSnapshots(int pageNum, int pageSize) {
        return statisticsSnapshotMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public Organization getOrganization() {
        return organizationMapper.selectOne(new LambdaQueryWrapper<Organization>().last("LIMIT 1"));
    }

    @Override
    public Organization updateOrganization(Organization organization) {
        // 前端旧版可能不传 id，机构信息表只有一行，默认取首行兜底
        if (organization.getId() == null) {
            Organization existing = organizationMapper.selectOne(new LambdaQueryWrapper<Organization>().last("LIMIT 1"));
            if (existing != null) organization.setId(existing.getId());
        } else {
            // L3: 校验指定 ID 的机构记录是否存在
            Organization existing = organizationMapper.selectById(organization.getId());
            if (existing == null) {
                throw new com.pzhu.eduadmin.common.BusinessException(404, "机构配置记录不存在");
            }
        }

        organizationMapper.updateById(organization);

        // 操作日志
        operationLogService.log("系统配置", "更新机构配置(id=" + organization.getId() + ")");

        return organizationMapper.selectById(organization.getId());
    }

    private static final Map<String, SFunction<OperationLog, ?>> OP_LOG_SORT_MAP = Map.of(
            "id", OperationLog::getId, "createTime", OperationLog::getCreateTime,
            "module", OperationLog::getModule, "operatorId", OperationLog::getOperatorId
    );

    @Override
    public Page<OperationLog> pageOperationLogs(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<OperationLog> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, OperationLog::getModule, OperationLog::getOperation);
        QueryHelper.applySort(wrapper, sortField, sortOrder, OP_LOG_SORT_MAP, () -> wrapper.orderByDesc(OperationLog::getCreateTime));
        Page<OperationLog> page = operationLogMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        if (!page.getRecords().isEmpty()) {
            Set<Long> operatorIds = page.getRecords().stream()
                    .map(OperationLog::getOperatorId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!operatorIds.isEmpty()) {
                List<User> users = userMapper.selectList(
                        new LambdaQueryWrapper<User>().in(User::getId, operatorIds));
                Map<Long, String> nameMap = users.stream()
                        .collect(Collectors.toMap(User::getId,
                                u -> u.getRealName() != null && !u.getRealName().isBlank()
                                        ? u.getRealName()
                                        : (u.getUsername() != null ? u.getUsername() : "用户" + u.getId()),
                                (a, b) -> a));
                page.getRecords().forEach(log -> {
                    String name = nameMap.get(log.getOperatorId());
                    log.setOperatorName(name != null ? name : "用户" + log.getOperatorId());
                });
            }
        }
        return page;
    }

    // ==================== Dashboard 聚合 ====================

    @Override
    public Map<String, Object> getDashboard() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cards", buildCards());
        result.put("charts", buildCharts());
        return result;
    }

    private Map<String, Object> buildCards() {
        Map<String, Object> cards = new LinkedHashMap<>();

        // 1. 在册学员数（按 student_id 去重：class_student 为学员-班级关联表，
        //    一个学员可报名多个班级，直接 COUNT(*) 得到的是「在班人次」，
        //    会导致看板数字与学员管理列表总数不一致）
        long activeStudents = classStudentMapper.countDistinctActiveStudents();
        cards.put("activeStudents", activeStudents);

        // 2. 本月课次
        YearMonth thisMonth = YearMonth.now();
        LocalDate monthStart = thisMonth.atDay(1);
        LocalDate monthEnd = thisMonth.atEndOfMonth();
        long monthlyLessons = scheduleLessonMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .ge(ScheduleLesson::getLessonDate, monthStart)
                        .le(ScheduleLesson::getLessonDate, monthEnd)
                        .eq(ScheduleLesson::getStatus, 2)); // 已完成
        cards.put("monthlyLessons", monthlyLessons);

        // 3. 本月营收
        BigDecimal monthlyRevenue = sumPaymentRevenue(monthStart, monthEnd);
        cards.put("monthlyRevenue", monthlyRevenue.setScale(2, RoundingMode.HALF_UP));

        // 4. 到课率（Medium fix: 分母含全部有效考勤 1=到场,2=迟到,3=请假,4=缺勤，与班级活动统计口径一致）
        long totalAttendance = attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().in(Attendance::getStatus, 1, 2, 3, 4));
        long attendedCount = attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().in(Attendance::getStatus, 1, 2)); // 到场+迟到算到课
        BigDecimal attendanceRate = totalAttendance > 0
                ? BigDecimal.valueOf(attendedCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(totalAttendance), 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        cards.put("attendanceRate", attendanceRate);

        return cards;
    }

    private Map<String, Object> buildCharts() {
        Map<String, Object> charts = new LinkedHashMap<>();

        // 1. 月度课时趋势（近6月）
        List<Map<String, Object>> lessonTrend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            LocalDate s = ym.atDay(1);
            LocalDate e = ym.atEndOfMonth();
            long count = scheduleLessonMapper.selectCount(
                    new LambdaQueryWrapper<ScheduleLesson>()
                            .ge(ScheduleLesson::getLessonDate, s)
                            .le(ScheduleLesson::getLessonDate, e)
                            .eq(ScheduleLesson::getStatus, 2));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", ym.format(fmt));
            item.put("count", count);
            lessonTrend.add(item);
        }
        charts.put("lessonTrend", lessonTrend);

        // 2. 营收统计（近6月）
        List<Map<String, Object>> revenueTrend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            LocalDate s = ym.atDay(1);
            LocalDate e = ym.atEndOfMonth();
            BigDecimal amount = sumPaymentRevenue(s, e);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", ym.format(fmt));
            item.put("amount", amount.setScale(2, RoundingMode.HALF_UP));
            revenueTrend.add(item);
        }
        charts.put("revenueTrend", revenueTrend);

        // 3. 到课率趋势（近6月）
        List<Map<String, Object>> attendanceTrend = new ArrayList<>();
        // Bug#43: 限制查询范围为近6个月，避免加载全量历史数据导致内存溢出
        LocalDate sixMonthsAgo = LocalDate.now().minusMonths(6).withDayOfMonth(1);
        List<ScheduleLesson> allLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getStatus, 2)
                        .ge(ScheduleLesson::getLessonDate, sixMonthsAgo));
        Set<Long> allLessonIds = allLessons.stream().map(ScheduleLesson::getId).collect(Collectors.toSet());
        // S4: 空集合 IN 防护 — 无已完成课次时跳过考勤查询，避免全表扫描
        List<Attendance> allAttendances;
        if (allLessonIds.isEmpty()) {
            allAttendances = Collections.emptyList();
        } else {
            allAttendances = attendanceMapper.selectList(
                    new LambdaQueryWrapper<Attendance>().in(Attendance::getLessonId, allLessonIds));
        }
        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            long lessonsInMonth = allLessons.stream().filter(l -> {
                LocalDate d = l.getLessonDate();
                return d != null && YearMonth.from(d).equals(ym);
            }).count();
            Set<Long> monthLessonIds = allLessons.stream().filter(l -> {
                LocalDate d = l.getLessonDate();
                return d != null && YearMonth.from(d).equals(ym);
            }).map(ScheduleLesson::getId).collect(Collectors.toSet());
            long attended = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId()) && a.getStatus() != null && (a.getStatus() == 1 || a.getStatus() == 2)).count();
            // M6 fix: 分母口径与仪表盘卡片(buildCards)统一，含 1=到场,2=迟到,3=请假,4=缺勤
            long total = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId()) && a.getStatus() != null && (a.getStatus() == 1 || a.getStatus() == 2 || a.getStatus() == 3 || a.getStatus() == 4)).count();
            BigDecimal rate = total > 0
                    ? BigDecimal.valueOf(attended).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", ym.format(fmt));
            item.put("rate", rate);
            attendanceTrend.add(item);
        }
        charts.put("attendanceTrend", attendanceTrend);

        return charts;
    }

    private BigDecimal sumPaymentRevenue(LocalDate start, LocalDate end) {
        List<PaymentRecord> records = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>()
                        .ge(PaymentRecord::getPayTime, start.atStartOfDay())
                        .lt(PaymentRecord::getPayTime, end.plusDays(1).atStartOfDay()));
        BigDecimal totalPayment = records.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 扣减同期已审核通过的退费金额
        List<RefundRecord> refunds = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>()
                        .eq(RefundRecord::getStatus, 2)
                        .ge(RefundRecord::getCreateTime, start.atStartOfDay())
                        .lt(RefundRecord::getCreateTime, end.plusDays(1).atStartOfDay()));
        BigDecimal totalRefund = refunds.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return totalPayment.subtract(totalRefund);
    }

    // ==================== 教师工作量统计 ====================

    @Override
    public List<Map<String, Object>> getTeacherWorkload(String month) {
        YearMonth ym;
        if (month != null && !month.isBlank()) {
            try {
                ym = YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (java.time.format.DateTimeParseException e) {
                throw new BusinessException(400, "month 日期格式不正确，应为 yyyy-MM");
            }
        } else {
            ym = YearMonth.now();
        }
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // 查询指定月份所有已完成课次
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .ge(ScheduleLesson::getLessonDate, monthStart)
                        .le(ScheduleLesson::getLessonDate, monthEnd)
                        .eq(ScheduleLesson::getStatus, 2));

        // 按教师聚合统计（过滤掉未分配教师的课次，避免 NPE）
        Map<Long, Long> teacherCountMap = lessons.stream()
                .filter(l -> l.getTeacherId() != null)
                .collect(Collectors.groupingBy(ScheduleLesson::getTeacherId, Collectors.counting()));

        // 获取教师姓名
        Set<Long> teacherIds = teacherCountMap.keySet();
        final Map<Long, String> nameMap;
        if (!teacherIds.isEmpty()) {
            List<User> users = userMapper.selectList(
                    new LambdaQueryWrapper<User>().in(User::getId, teacherIds));
            nameMap = users.stream().collect(Collectors.toMap(User::getId,
                    u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername(),
                    (a, b) -> a));
        } else {
            nameMap = Collections.emptyMap();
        }

        return teacherCountMap.entrySet().stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("teacherId", e.getKey());
            item.put("teacherName", nameMap.getOrDefault(e.getKey(), "教师" + e.getKey()));
            item.put("lessonCount", e.getValue());
            return item;
        }).collect(Collectors.toList());
    }

    // ==================== 学员流失率趋势 ====================

    @Override
    public List<Map<String, Object>> getStudentLossTrend() {
        List<Map<String, Object>> trend = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 5; i >= 0; i--) {
            YearMonth ym = YearMonth.now().minusMonths(i);
            LocalDate monthStart = ym.atDay(1);
            LocalDate monthEnd = ym.atEndOfMonth();

            // Bug#4 修复: 使用时间逻辑代替当前状态过滤，避免历史月份数据偏低
            // 月初在班人数 = 月初之前入班 且 在月初时仍在班（当前仍活跃，或离班时间>=月初）
            long beginCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .lt(ClassStudent::getJoinTime, monthStart.atStartOfDay())
                            .and(w -> w
                                    .eq(ClassStudent::getStatus, 1)
                                    .or(o -> o
                                            .ne(ClassStudent::getStatus, 1)
                                            .ge(ClassStudent::getUpdateTime, monthStart.atStartOfDay()))));

            // 本月新入班人数（不论当前状态，只要入班时间在本月内）
            long newJoinCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .ge(ClassStudent::getJoinTime, monthStart.atStartOfDay())
                            .lt(ClassStudent::getJoinTime, monthEnd.plusDays(1).atStartOfDay()));

            // 本月退班人数（status = 3 且 updateTime 在本月）
            long lossCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getStatus, 3)
                            .ge(ClassStudent::getUpdateTime, monthStart.atStartOfDay())
                            .lt(ClassStudent::getUpdateTime, monthEnd.plusDays(1).atStartOfDay()));

            // 分母 = 月初在班 + 本月新入班（本月曾处于在班状态的总人数）
            long denominator = beginCount + newJoinCount;
            BigDecimal rate = denominator > 0
                    ? BigDecimal.valueOf(lossCount).multiply(BigDecimal.valueOf(100))
                            .divide(BigDecimal.valueOf(denominator), 1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", ym.format(fmt));
            item.put("activeCount", beginCount);
            item.put("lossCount", lossCount);
            item.put("lossRate", rate);
            trend.add(item);
        }
        return trend;
    }

    @Override
    public List<Map<String, Object>> getClassActivity() {
        List<ClassGroup> classes = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroup>().eq(ClassGroup::getStatus, 1));
        if (classes.isEmpty()) return Collections.emptyList();

        Set<Long> classIds = classes.stream().map(ClassGroup::getId).collect(Collectors.toSet());

        // 当前学员数
        Map<Long, Long> studentCount = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .in(ClassStudent::getClassId, classIds)
                        .eq(ClassStudent::getStatus, 1))
                .stream().collect(Collectors.groupingBy(ClassStudent::getClassId, Collectors.counting()));

        // 课次统计
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, classIds)
                        .select(ScheduleLesson::getId, ScheduleLesson::getClassId, ScheduleLesson::getStatus, ScheduleLesson::getLessonDate));

        Map<Long, List<ScheduleLesson>> lessonByClass = lessons.stream()
                .collect(Collectors.groupingBy(ScheduleLesson::getClassId));

        Set<Long> allLessonIds = lessons.stream().map(ScheduleLesson::getId).collect(Collectors.toSet());

        // 出勤率
        Map<Long, Long> attendancePresent = new HashMap<>();
        Map<Long, Long> attendanceTotal = new HashMap<>();
        if (!allLessonIds.isEmpty()) {
            // L4 fix: 构建 Map 替代 O(n×m) 线性扫描
            Map<Long, ScheduleLesson> lessonMap = lessons.stream()
                    .collect(Collectors.toMap(ScheduleLesson::getId, l -> l));
            List<Attendance> attendances = attendanceMapper.selectList(
                    new LambdaQueryWrapper<Attendance>().in(Attendance::getLessonId, allLessonIds));
            for (Attendance a : attendances) {
                ScheduleLesson sl = lessonMap.get(a.getLessonId());
                if (sl == null) continue;
                // M6 fix: 分母口径与仪表盘卡片统一，仅统计有效考勤状态 1=到场,2=迟到,3=请假,4=缺勤
                if (a.getStatus() == null || a.getStatus() < 1 || a.getStatus() > 4) continue;
                Long cid = sl.getClassId();
                attendanceTotal.merge(cid, 1L, Long::sum);
                if (a.getStatus() == 1 || a.getStatus() == 2) {
                    attendancePresent.merge(cid, 1L, Long::sum);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (ClassGroup cg : classes) {
            long sc = studentCount.getOrDefault(cg.getId(), 0L);
            List<ScheduleLesson> clsLessons = lessonByClass.getOrDefault(cg.getId(), Collections.emptyList());
            long completed = clsLessons.stream().filter(l -> Integer.valueOf(2).equals(l.getStatus())).count();
            long totalLessons = clsLessons.size();
            long attPresent = attendancePresent.getOrDefault(cg.getId(), 0L);
            long attTotal = attendanceTotal.getOrDefault(cg.getId(), 0L);
            double attRate = attTotal > 0 ? Math.round((double) attPresent / attTotal * 100.0) / 100.0 : 0.0;
            LocalDate lastDate = clsLessons.stream()
                    .map(ScheduleLesson::getLessonDate).filter(Objects::nonNull)
                    .max(LocalDate::compareTo).orElse(null);

            Map<String, Object> item = new LinkedHashMap<>();
            item.put("classId", cg.getId());
            item.put("className", cg.getClassName());
            item.put("studentCount", sc);
            item.put("maxStudentCount", cg.getMaxStudentCount() == null ? 0 : cg.getMaxStudentCount());
            item.put("completedLessons", completed);
            item.put("totalLessons", totalLessons);
            item.put("attendanceRate", attRate);
            item.put("lastLessonDate", lastDate != null ? lastDate.toString() : null);
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getCourseProfit() {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().orderByDesc(Course::getId));
        if (courses.isEmpty()) return Collections.emptyList();

        // M27: 仅加载现有课程的缴费记录，避免全表加载 OOM
        Set<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());
        List<PaymentRecord> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().in(PaymentRecord::getCourseId, courseIds));
        List<RefundRecord> refunds = refundRecordMapper.selectList(
                new LambdaQueryWrapper<RefundRecord>().eq(RefundRecord::getStatus, 2));

        Map<Long, BigDecimal> incomeMap = new HashMap<>();
        for (PaymentRecord p : payments) {
            if (p.getCourseId() != null) {
                incomeMap.merge(p.getCourseId(), p.getAmount() != null ? p.getAmount() : BigDecimal.ZERO, BigDecimal::add);
            }
        }

        // 退费通过enrollmentId→enrollment→courseId 映射
        Map<Long, BigDecimal> refundMap = new HashMap<>();
        if (!refunds.isEmpty()) {
            Set<Long> enrollmentIds = refunds.stream().map(RefundRecord::getEnrollmentId)
                    .filter(Objects::nonNull).collect(Collectors.toSet());
            Map<Long, Enrollment> enrollmentMap = Collections.emptyMap();
            if (!enrollmentIds.isEmpty()) {
                // Medium fix: 用 IncludeDeleted 查询，避免已退班（逻辑删除）报名的退费被丢弃导致利润高估
                enrollmentMap = enrollmentMapper.selectBatchIdsIncludeDeleted(enrollmentIds).stream()
                        .collect(Collectors.toMap(Enrollment::getId, e -> e, (a, b) -> a));
            }
            for (RefundRecord r : refunds) {
                if (r.getEnrollmentId() != null && r.getAmount() != null) {
                    Enrollment en = enrollmentMap.get(r.getEnrollmentId());
                    if (en != null && en.getCourseId() != null) {
                        refundMap.merge(en.getCourseId(), r.getAmount(), BigDecimal::add);
                    }
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) {
            BigDecimal income = incomeMap.getOrDefault(c.getId(), BigDecimal.ZERO);
            BigDecimal refund = refundMap.getOrDefault(c.getId(), BigDecimal.ZERO);
            BigDecimal net = income.subtract(refund);
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", c.getId());
            item.put("courseName", c.getName());
            item.put("income", income.setScale(2, RoundingMode.HALF_UP));
            item.put("refund", refund.setScale(2, RoundingMode.HALF_UP));
            item.put("netProfit", net.setScale(2, RoundingMode.HALF_UP));
            result.add(item);
        }
        return result;
    }

    @Override
    public List<Map<String, Object>> getPaymentRate() {
        List<Course> courses = courseMapper.selectList(
                new LambdaQueryWrapper<Course>().orderByDesc(Course::getId));
        if (courses.isEmpty()) return Collections.emptyList();

        // Bug#39: 使用报名数量比率代替金额比率，避免课程调价导致历史数据不准确
        // M27: 仅加载现有课程的缴费记录，避免全表加载 OOM
        Set<Long> courseIds = courses.stream().map(Course::getId).collect(Collectors.toSet());
        List<PaymentRecord> payments = paymentRecordMapper.selectList(
                new LambdaQueryWrapper<PaymentRecord>().in(PaymentRecord::getCourseId, courseIds));
        Set<Long> paidEnrollmentIds = payments.stream()
                .map(PaymentRecord::getEnrollmentId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 报名记录（仅审核通过/已缴费状态）
        List<Enrollment> enrollments = enrollmentMapper.selectList(
                new LambdaQueryWrapper<Enrollment>().in(Enrollment::getStatus, 2, 3));

        // 按课程统计：总报名数 vs 已缴费报名数
        Map<Long, Long> totalMap = new HashMap<>();
        Map<Long, Long> paidCountMap = new HashMap<>();
        for (Enrollment e : enrollments) {
            if (e.getCourseId() != null) {
                totalMap.merge(e.getCourseId(), 1L, Long::sum);
                if (paidEnrollmentIds.contains(e.getId())) {
                    paidCountMap.merge(e.getCourseId(), 1L, Long::sum);
                }
            }
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Course c : courses) {
            long total = totalMap.getOrDefault(c.getId(), 0L);
            long paid = paidCountMap.getOrDefault(c.getId(), 0L);
            double rate = total > 0
                    ? BigDecimal.valueOf(paid).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP).doubleValue()
                    : 0.0;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("courseId", c.getId());
            item.put("courseName", c.getName());
            item.put("expected", total);
            item.put("paid", paid);
            item.put("rate", rate);
            result.add(item);
        }
        return result;
    }
}
