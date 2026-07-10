package com.pzhu.eduadmin.modules.statistics.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.mapper.EnrollmentMapper;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.mapper.PaymentRecordMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.entity.Organization;
import com.pzhu.eduadmin.modules.statistics.entity.StatisticsSnapshot;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.OrganizationMapper;
import com.pzhu.eduadmin.modules.statistics.mapper.StatisticsSnapshotMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
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
    private final EnrollmentMapper enrollmentMapper;
    private final AttendanceMapper attendanceMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final PaymentRecordMapper paymentRecordMapper;
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
        organizationMapper.updateById(organization);

        // 操作日志
        OperationLog opLog = new OperationLog();
        opLog.setOperatorId(CurrentUserHolder.get().getUserId());
        opLog.setModule("系统配置");
        opLog.setOperation("更新机构配置(id=" + organization.getId() + ")");
        opLog.setIp("0.0.0.0");
        operationLogMapper.insert(opLog);

        return organizationMapper.selectById(organization.getId());
    }

    @Override
    public Page<OperationLog> pageOperationLogs(int pageNum, int pageSize) {
        return operationLogMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<OperationLog>().orderByDesc(OperationLog::getCreateTime));
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

        // 4. 到课率
        long totalAttendance = attendanceMapper.selectCount(new LambdaQueryWrapper<>());
        long attendedCount = attendanceMapper.selectCount(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStatus, 1));
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
        List<Attendance> allAttendances = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().in(Attendance::getLessonId, allLessonIds));
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
            long attended = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId()) && a.getStatus() == 1).count();
            long total = allAttendances.stream().filter(a -> monthLessonIds.contains(a.getLessonId())).count();
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
                        .le(PaymentRecord::getPayTime, end.atTime(23, 59, 59)));
        return records.stream()
                .map(r -> r.getAmount() != null ? r.getAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
