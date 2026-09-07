package com.pzhu.eduadmin.modules.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.risk.RiskRuleProperties;
import com.pzhu.eduadmin.modules.risk.RiskScoreResult;
import com.pzhu.eduadmin.modules.risk.RiskScoringEngine;
import com.pzhu.eduadmin.modules.risk.RiskStudentFacts;
import com.pzhu.eduadmin.modules.risk.dto.RiskStudentVO;
import com.pzhu.eduadmin.modules.risk.dto.RiskSummaryVO;
import com.pzhu.eduadmin.modules.risk.entity.StudentRiskFollowup;
import com.pzhu.eduadmin.modules.risk.mapper.StudentRiskFollowupMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 流失预警清单实现。
 *
 * <p>实时计算（决策 D6：不写 statistics_snapshot）。候选 = class_student.status=1
 * 且 student.status=1 的在班学员；对每个"在班(班级×学员)"计算多因子风险分；0 分不进入名单。</p>
 */
@Service
@RequiredArgsConstructor
public class RiskWarningServiceImpl implements RiskWarningService {

    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CourseMapper courseMapper;
    private final StudentMapper studentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final AttendanceMapper attendanceMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final StudentRiskFollowupMapper followupMapper;
    private final NotificationService notificationService;
    private final RiskRuleProperties ruleProperties;

    private final RiskScoringEngine scoringEngine = new RiskScoringEngine();

    /** 考勤事件（学员×班级维度） */
    private record AttendEvent(LocalDate lessonDate, Integer status, Long lessonId) {
    }

    @Override
    public List<RiskStudentVO> listRiskWarnings(String level, Long classId, Long courseId,
                                                String keyword, Integer followUpStatus) {
        LocalDate today = LocalDate.now();
        int windowDays = Math.max(1, ruleProperties.getAttendanceWindowDays());
        int lookbackDays = Math.max(windowDays, Math.max(1, ruleProperties.getAbsenceLookbackDays()));
        LocalDate lookbackStart = today.minusDays(lookbackDays);

        List<ClassStudent> members = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getStatus, 1));
        if (members.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> allClassIds = members.stream().map(ClassStudent::getClassId).collect(Collectors.toSet());
        Set<Long> allStudentIds = members.stream().map(ClassStudent::getStudentId).collect(Collectors.toSet());

        Map<Long, ClassGroup> classMap = toMap(
                classGroupMapper.selectBatchIds(allClassIds), ClassGroup::getId);
        Map<Long, Student> studentMap = studentMapper.selectBatchIds(allStudentIds).stream()
                .filter(s -> Integer.valueOf(1).equals(s.getStatus()))
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
        if (classMap.isEmpty() || studentMap.isEmpty()) {
            return Collections.emptyList();
        }

        // 班级级筛选（classId / courseId）
        Set<Long> effectiveClassIds = new HashSet<>();
        for (Long cid : allClassIds) {
            ClassGroup cg = classMap.get(cid);
            if (cg == null) continue;
            if (classId != null && !classId.equals(cid)) continue;
            if (courseId != null && !courseId.equals(cg.getCourseId())) continue;
            effectiveClassIds.add(cid);
        }
        if (effectiveClassIds.isEmpty()) {
            return Collections.emptyList();
        }
        // 有效候选：班级在筛选内 + 学员在册
        List<ClassStudent> candidates = members.stream()
                .filter(cs -> effectiveClassIds.contains(cs.getClassId()))
                .filter(cs -> studentMap.containsKey(cs.getStudentId()))
                .toList();
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        // 课程名（含历史软删课程）
        Set<Long> courseIds = effectiveClassIds.stream().map(classMap::get)
                .filter(Objects::nonNull).map(ClassGroup::getCourseId).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            for (Map<String, Object> row : courseMapper.selectNamesByIdsIncludeDeleted(courseIds)) {
                courseNameMap.put(((Number) row.get("id")).longValue(), String.valueOf(row.get("name")));
            }
        }

        // 课次（覆盖 lookback 窗口）
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, effectiveClassIds)
                        .ge(ScheduleLesson::getLessonDate, lookbackStart)
                        .in(ScheduleLesson::getStatus, 1, 2));
        Map<Long, List<ScheduleLesson>> lessonsByClass = lessons.stream()
                .filter(l -> l.getClassId() != null)
                .collect(Collectors.groupingBy(ScheduleLesson::getClassId));
        Map<Long, ScheduleLesson> lessonById = lessons.stream()
                .filter(l -> l.getId() != null)
                .collect(Collectors.toMap(ScheduleLesson::getId, Function.identity(), (a, b) -> a));

        // 考勤 → 学员×班级事件
        Map<Long, Map<Long, List<AttendEvent>>> eventsByStudentClass = new HashMap<>();
        if (!lessonById.isEmpty()) {
            List<Attendance> attendances = attendanceMapper.selectList(
                    new LambdaQueryWrapper<Attendance>().in(Attendance::getLessonId, lessonById.keySet()));
            for (Attendance att : attendances) {
                if (att.getStudentId() == null || att.getStatus() == null
                        || att.getStatus() < 1 || att.getStatus() > 4) {
                    continue;
                }
                ScheduleLesson lesson = lessonById.get(att.getLessonId());
                if (lesson == null || lesson.getClassId() == null || lesson.getLessonDate() == null) {
                    continue;
                }
                eventsByStudentClass
                        .computeIfAbsent(att.getStudentId(), k -> new HashMap<>())
                        .computeIfAbsent(lesson.getClassId(), k -> new ArrayList<>())
                        .add(new AttendEvent(lesson.getLessonDate(), att.getStatus(), att.getLessonId()));
            }
        }

        // 课时账户
        Map<Long, List<LessonAccount>> accountsByStudent = lessonAccountMapper.selectList(
                        new LambdaQueryWrapper<LessonAccount>().in(LessonAccount::getStudentId, allStudentIds))
                .stream().filter(a -> a.getStudentId() != null)
                .collect(Collectors.groupingBy(LessonAccount::getStudentId));

        // 跟进状态
        Map<Long, StudentRiskFollowup> followupByStudent = followupMapper.selectList(new LambdaQueryWrapper<>())
                .stream().collect(Collectors.toMap(StudentRiskFollowup::getStudentId, Function.identity(), (a, b) -> a));

        List<RiskStudentVO> rows = new ArrayList<>();
        for (ClassStudent cs : candidates) {
            RiskStudentVO vo = buildRow(cs, classMap, studentMap, courseNameMap,
                    lessonsByClass, eventsByStudentClass, accountsByStudent, followupByStudent);
            if (vo == null) {
                continue;
            }
            rows.add(vo);
        }

        // 内存筛选（档位 / 姓名 / 跟进状态）
        List<RiskStudentVO> filtered = rows.stream()
                .filter(vo -> level == null || level.equalsIgnoreCase(vo.getRiskLevel()))
                .filter(vo -> keyword == null || keyword.isBlank()
                        || (vo.getStudentName() != null && vo.getStudentName().toLowerCase().contains(keyword.trim().toLowerCase())))
                .filter(vo -> followUpStatus == null || Objects.equals(followUpStatus, vo.getFollowUpStatus()))
                .sorted(Comparator.comparing(RiskStudentVO::getRiskScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RiskStudentVO::getStudentId, Comparator.nullsLast(Long::compareTo)))
                .collect(Collectors.toList());
        return filtered;
    }

    private RiskStudentVO buildRow(ClassStudent cs,
                                   Map<Long, ClassGroup> classMap,
                                   Map<Long, Student> studentMap,
                                   Map<Long, String> courseNameMap,
                                   Map<Long, List<ScheduleLesson>> lessonsByClass,
                                   Map<Long, Map<Long, List<AttendEvent>>> eventsByStudentClass,
                                   Map<Long, List<LessonAccount>> accountsByStudent,
                                   Map<Long, StudentRiskFollowup> followupByStudent) {
        LocalDate today = LocalDate.now();
        ClassGroup cg = classMap.get(cs.getClassId());
        Student st = studentMap.get(cs.getStudentId());
        if (cg == null || st == null) {
            return null;
        }

        int windowDays = Math.max(1, ruleProperties.getAttendanceWindowDays());
        LocalDate windowStart = today.minusDays(windowDays);
        int absenceLookback = Math.max(1, ruleProperties.getAbsenceLookbackDays());

        List<ScheduleLesson> classLessons = lessonsByClass.getOrDefault(cs.getClassId(), Collections.emptyList());
        boolean classHasRecentSchedule = classLessons.stream()
                .anyMatch(l -> l.getLessonDate() != null && !l.getLessonDate().isBefore(today.minusDays(absenceLookback)));

        List<AttendEvent> events = new ArrayList<>();
        Map<Long, List<AttendEvent>> perClass = eventsByStudentClass.get(cs.getStudentId());
        if (perClass != null) {
            events = perClass.getOrDefault(cs.getClassId(), Collections.emptyList());
        }

        // F1：最近到课/迟到
        LocalDate lastAttend = events.stream()
                .filter(e -> e.status() != null && (e.status() == 1 || e.status() == 2))
                .map(AttendEvent::lessonDate)
                .max(LocalDate::compareTo)
                .orElse(null);

        // F2：近 window 天考勤(1/2/4)计数
        int scheduledCount = 0;
        int absentCount = 0;
        for (AttendEvent e : events) {
            if (e.lessonDate() == null || e.lessonDate().isBefore(windowStart)) {
                continue;
            }
            if (e.status() == 1 || e.status() == 2 || e.status() == 4) {
                scheduledCount++;
                if (e.status() == 4) {
                    absentCount++;
                }
            }
        }

        // F5（近 8 周）：连续缺勤 >=2 或已批请假 >=3
        LocalDate week8Start = today.minusDays(56);
        List<AttendEvent> recent8w = events.stream()
                .filter(e -> e.lessonDate() != null && !e.lessonDate().isBefore(week8Start))
                .sorted(Comparator.comparing(AttendEvent::lessonDate).thenComparing(AttendEvent::lessonId))
                .collect(Collectors.toList());
        boolean consecutiveAbsenceStreak = false;
        int approvedLeaveCount8w = 0;
        int run = 0;
        for (AttendEvent e : recent8w) {
            if (e.status() == 3) {
                approvedLeaveCount8w++;
                run = 0;
            } else if (e.status() == 4) {
                run++;
                if (run >= 2) {
                    consecutiveAbsenceStreak = true;
                }
            } else if (e.status() == 1 || e.status() == 2) {
                run = 0;
            }
        }

        // 账户快照
        List<RiskStudentFacts.AccountSnapshot> accountSnapshots = accountsByStudent
                .getOrDefault(cs.getStudentId(), Collections.emptyList()).stream()
                .map(a -> RiskStudentFacts.AccountSnapshot.builder()
                        .remainingLessons(a.getRemainingLessons())
                        .totalLessons(a.getTotalLessons())
                        .expireDate(a.getExpireDate())
                        .build())
                .collect(Collectors.toList());

        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(classHasRecentSchedule)
                .lastAttendDate(lastAttend)
                .scheduledCount28d(scheduledCount)
                .absentCount28d(absentCount)
                .accounts(accountSnapshots)
                .consecutiveAbsenceStreak(consecutiveAbsenceStreak)
                .approvedLeaveCount8w(approvedLeaveCount8w)
                .build();

        RiskScoreResult result = scoringEngine.score(facts, ruleProperties);
        if (result.getTotal() <= 0) {
            return null;
        }

        RiskStudentVO vo = new RiskStudentVO();
        vo.setStudentId(st.getId());
        vo.setStudentName(st.getName());
        vo.setClassId(cg.getId());
        vo.setClassName(cg.getClassName());
        vo.setCourseId(cg.getCourseId());
        vo.setCourseName(cg.getCourseId() != null ? courseNameMap.getOrDefault(cg.getCourseId(), "") : "");
        vo.setRiskScore(result.getTotal());
        vo.setRiskLevel(result.getRiskLevel());
        vo.setF1(result.getF1());
        vo.setF2(result.getF2());
        vo.setF3(result.getF3());
        vo.setF4(result.getF4());
        vo.setF5(result.getF5());
        vo.setLastAttendDate(lastAttend);
        vo.setScheduledCount28d(scheduledCount);
        vo.setAbsentCount28d(absentCount);
        vo.setAbsenceRate28d(scheduledCount > 0
                ? roundHalfUp(absentCount * 100.0 / scheduledCount, 1) : 0.0);
        vo.setRemainingLessons(result.getWorstRemainingLessons());
        vo.setTotalLessons(result.getWorstTotalLessons());
        vo.setExpireDate(result.getWorstExpireDate());
        vo.setDaysToExpire(result.getDaysToExpire());
        vo.setSuggestedAction(result.getSuggestedAction());

        StudentRiskFollowup fu = followupByStudent.get(st.getId());
        vo.setFollowUpStatus(fu != null && fu.getStatus() != null ? fu.getStatus() : StudentRiskFollowup.STATUS_PENDING);
        vo.setFollowUpRemark(fu != null ? fu.getRemark() : null);
        return vo;
    }

    @Override
    public PageResult<RiskStudentVO> pageRiskWarnings(int pageNum, int pageSize, String level, Long classId,
                                                      Long courseId, String keyword, Integer followUpStatus) {
        List<RiskStudentVO> all = listRiskWarnings(level, classId, courseId, keyword, followUpStatus);
        int total = all.size();
        int from = Math.min((pageNum - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        return PageResult.of(total, new ArrayList<>(all.subList(from, to)));
    }

    @Override
    public RiskSummaryVO summary() {
        List<RiskStudentVO> all = listRiskWarnings(null, null, null, null, null);
        RiskSummaryVO summaryVO = new RiskSummaryVO();
        long high = all.stream().filter(v -> RiskScoreResult.LEVEL_HIGH.equals(v.getRiskLevel())).count();
        long medium = all.stream().filter(v -> RiskScoreResult.LEVEL_MEDIUM.equals(v.getRiskLevel())).count();
        long low = all.stream().filter(v -> RiskScoreResult.LEVEL_LOW.equals(v.getRiskLevel())).count();
        summaryVO.setTotal(all.size());
        summaryVO.setHigh(high);
        summaryVO.setMedium(medium);
        summaryVO.setLow(low);
        List<RiskSummaryVO.LevelCount> byLevel = new ArrayList<>();
        byLevel.add(RiskSummaryVO.LevelCount.of("HIGH", high));
        byLevel.add(RiskSummaryVO.LevelCount.of("MEDIUM", medium));
        byLevel.add(RiskSummaryVO.LevelCount.of("LOW", low));
        summaryVO.setByLevel(byLevel);
        return summaryVO;
    }

    @Override
    public StudentRiskFollowup updateFollowUp(Long studentId, Integer status, String remark, Long operatorId) {
        if (studentId == null) {
            throw new BusinessException(400, "学员ID不能为空");
        }
        if (status == null || (status != StudentRiskFollowup.STATUS_PENDING
                && status != StudentRiskFollowup.STATUS_FOLLOWED
                && status != StudentRiskFollowup.STATUS_SKIP)) {
            throw new BusinessException(400, "跟进状态只能为 0（待跟进）/1（已跟进）/2（暂不跟进）");
        }
        StudentRiskFollowup existing = followupMapper.selectOne(new LambdaQueryWrapper<StudentRiskFollowup>()
                .eq(StudentRiskFollowup::getStudentId, studentId)
                .last("LIMIT 1"));
        LocalDateTime now = LocalDateTime.now();
        if (existing == null) {
            existing = new StudentRiskFollowup();
            existing.setStudentId(studentId);
            existing.setStatus(status);
            existing.setRemark(remark);
            existing.setOperatorId(operatorId);
            if (status == StudentRiskFollowup.STATUS_FOLLOWED) {
                existing.setFollowupTime(now);
            }
            followupMapper.insert(existing);
        } else {
            existing.setStatus(status);
            existing.setRemark(remark);
            existing.setOperatorId(operatorId);
            if (status == StudentRiskFollowup.STATUS_FOLLOWED) {
                existing.setFollowupTime(now);
            }
            followupMapper.updateById(existing);
        }
        return existing;
    }

    @Override
    public Map<String, Object> notifyParents(List<Long> studentIds, String message) {
        if (studentIds == null || studentIds.isEmpty()) {
            throw new BusinessException(400, "请先选择学员");
        }
        Set<Long> studentIdSet = new HashSet<>(studentIds);
        List<ParentStudent> links = parentStudentMapper.selectList(
                new LambdaQueryWrapper<ParentStudent>().in(ParentStudent::getStudentId, studentIdSet));
        Set<Long> parentIds = links.stream()
                .map(ParentStudent::getParentUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        String content = (message == null || message.isBlank())
                ? "您孩子的到课/课时情况需要关注，请及时与机构沟通。"
                : message.trim();
        LocalDate today = LocalDate.now();
        int notified = 0;
        for (Long parentId : parentIds) {
            Notification notification = new Notification();
            notification.setUserId(parentId);
            notification.setType("RISK_REMINDER");
            notification.setTitle("流失风险提醒");
            notification.setContent(content);
            notification.setRelatedId(null);
            // 当日幂等：同一家长当天只提醒一次
            if (notificationService.sendOnce(parentId, notification,
                    "RISK_FOLLOWUP:" + parentId + ":" + today)) {
                notified++;
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("parentCount", parentIds.size());
        result.put("notifiedParentCount", notified);
        return result;
    }

    private <T, K> Map<K, T> toMap(List<T> list, Function<T, K> keyFn) {
        if (list == null || list.isEmpty()) {
            return Collections.emptyMap();
        }
        return list.stream().collect(Collectors.toMap(keyFn, Function.identity(), (a, b) -> a));
    }

    private double roundHalfUp(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }
}
