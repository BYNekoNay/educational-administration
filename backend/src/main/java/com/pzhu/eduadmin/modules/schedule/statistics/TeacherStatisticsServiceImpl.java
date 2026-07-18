package com.pzhu.eduadmin.modules.schedule.statistics;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherStatisticsServiceImpl implements TeacherStatisticsService {

    private final ScheduleLessonMapper scheduleLessonMapper;
    private final AttendanceMapper attendanceMapper;

    @Override
    public Map<String, Object> getOverview(Long teacherId) {
        YearMonth thisMonth = YearMonth.now();
        YearMonth lastMonth = thisMonth.minusMonths(1);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("thisMonth", buildMonthStat(teacherId, thisMonth));
        result.put("lastMonth", buildMonthStat(teacherId, lastMonth));
        result.put("completionRate", calcCompletionRate(teacherId));
        result.put("attendanceRate", calcAttendanceRate(teacherId));
        result.put("substituteCount", countSubstituteLessons(teacherId));
        return result;
    }

    @Override
    public List<Map<String, Object>> getMonthly(Long teacherId, Integer year) {
        List<ScheduleLesson> allLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .orderByAsc(ScheduleLesson::getLessonDate));

        Map<String, MonthAccum> accums = new LinkedHashMap<>();
        for (ScheduleLesson l : allLessons) {
            if (l.getLessonDate() == null) continue;
            if (year != null && l.getLessonDate().getYear() != year) continue;
            String key = l.getLessonDate().getYear() + "-" + String.format("%02d", l.getLessonDate().getMonthValue());
            accums.computeIfAbsent(key, k -> new MonthAccum()).add(l);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, MonthAccum> e : accums.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("month", e.getKey());
            item.put("lessonCount", e.getValue().lessonCount);
            item.put("totalHours", e.getValue().totalHours);
            item.put("substituteCount", e.getValue().substituteCount);
            item.put("studentCount", countDistinctStudents(e.getValue().lessonIds));
            result.add(item);
        }
        return result;
    }

    // ===== private helpers =====

    private Map<String, Object> buildMonthStat(Long teacherId, YearMonth ym) {
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .ge(ScheduleLesson::getLessonDate, from)
                        .le(ScheduleLesson::getLessonDate, to));

        long lessonCount = lessons.size();
        long totalHours = lessons.stream()
                .mapToLong(l -> {
                    try {
                        return java.time.Duration.between(l.getStartTime(), l.getEndTime()).toMinutes();
                    } catch (Exception ex) { return 60; } // 默认1小时
                }).sum() / 60;

        long studentCount = countDistinctStudents(
                lessons.stream().map(ScheduleLesson::getId).collect(java.util.stream.Collectors.toSet()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("lessonCount", lessonCount);
        m.put("totalHours", totalHours);
        m.put("studentCount", studentCount);
        return m;
    }

    private double calcCompletionRate(Long teacherId) {
        long total = scheduleLessonMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLesson>().eq(ScheduleLesson::getTeacherId, teacherId));
        if (total == 0) return 0.0;
        long completed = scheduleLessonMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 2)); // 已完成
        return Math.round((double) completed / total * 100.0) / 100.0;
    }

    private double calcAttendanceRate(Long teacherId) {
        java.util.List<Long> lessonIds = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .select(ScheduleLesson::getId))
                .stream().map(ScheduleLesson::getId).collect(Collectors.toList());

        if (lessonIds.isEmpty()) return 0.0;

        long total = attendanceMapper.selectCount(new LambdaQueryWrapper<com.pzhu.eduadmin.modules.attendance.entity.Attendance>()
                .in(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getLessonId, (Collection<Long>) lessonIds));
        if (total == 0) return 0.0;

        long present = attendanceMapper.selectCount(new LambdaQueryWrapper<com.pzhu.eduadmin.modules.attendance.entity.Attendance>()
                .in(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getLessonId, (Collection<Long>) lessonIds)
                .in(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getStatus, List.of(1, 2)));
        return Math.round((double) present / total * 100.0) / 100.0;
    }

    private long countDistinctStudents(Set<Long> lessonIds) {
        if (lessonIds.isEmpty()) return 0;
        return attendanceMapper.selectList(
                new LambdaQueryWrapper<com.pzhu.eduadmin.modules.attendance.entity.Attendance>()
                        .in(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getLessonId, (Collection<Long>) lessonIds)
                        .select(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getStudentId))
                .stream().map(com.pzhu.eduadmin.modules.attendance.entity.Attendance::getStudentId).distinct().count();
    }

    private long countSubstituteLessons(Long teacherId) {
        return scheduleLessonMapper.selectCount(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .isNotNull(ScheduleLesson::getSourceLessonId));
    }

    private static class MonthAccum {
        long lessonCount = 0;
        long totalHours = 0;
        long substituteCount = 0;
        Set<Long> lessonIds = new HashSet<>();
        void add(ScheduleLesson l) {
            lessonCount++;
            if (l.getSourceLessonId() != null) substituteCount++;
            long dur = 60;
            try { dur = java.time.Duration.between(l.getStartTime(), l.getEndTime()).toMinutes(); } catch (Exception ignored) {}
            totalHours += dur;
            if (l.getId() != null) lessonIds.add(l.getId());
        }
    }
}
