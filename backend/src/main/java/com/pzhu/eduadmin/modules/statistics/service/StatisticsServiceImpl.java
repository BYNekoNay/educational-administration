package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
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
                                        ? u.getRealName() : u.getUsername(),
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

        // 1. 在册学员数
        long activeStudents = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStatus, 1));
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

        // 4. 到课率（Issue #23: 仅统计有效考勤状态 1=到场, 2=迟到, 3=请假）
        long totalAttendance = attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().in(Attendance::getStatus, 1, 2, 3));
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
        List<ScheduleLesson> allLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>().eq(ScheduleLesson::getStatus, 2));
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
            long attended = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId()) && (a.getStatus() == 1 || a.getStatus() == 2)).count();
            long total = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId()) && (a.getStatus() == 1 || a.getStatus() == 2 || a.getStatus() == 3)).count();
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
        YearMonth ym = (month != null && !month.isBlank())
                ? YearMonth.parse(month, DateTimeFormatter.ofPattern("yyyy-MM"))
                : YearMonth.now();
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // 查询指定月份所有已完成课次
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .ge(ScheduleLesson::getLessonDate, monthStart)
                        .le(ScheduleLesson::getLessonDate, monthEnd)
                        .eq(ScheduleLesson::getStatus, 2));

        // 按教师聚合统计
        Map<Long, Long> teacherCountMap = lessons.stream()
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

            // M4 修复: 月初在班人数 = 月初之前入班且仍活跃的学员
            long beginCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getStatus, 1)
                            .lt(ClassStudent::getJoinTime, monthStart.atStartOfDay()));

            // 本月新入班人数
            long newJoinCount = classStudentMapper.selectCount(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getStatus, 1)
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
}
