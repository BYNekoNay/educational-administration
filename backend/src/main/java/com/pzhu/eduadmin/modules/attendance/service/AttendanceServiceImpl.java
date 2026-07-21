package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.mapper.LeaveRequestMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ClassroomMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final LeaveRequestMapper leaveRequestMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassroomMapper classroomMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final LessonFlowMapper lessonFlowMapper;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final StudentMapper studentMapper;
    private final UserMapper userMapper;

    private static final Map<String, SFunction<Attendance, ?>> ATTENDANCE_SORT_MAP = Map.of(
            "id", Attendance::getId,
            "checkTime", Attendance::getCheckTime,
            "status", Attendance::getStatus
    );

    @Override
    public Page<Attendance> page(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<Attendance> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, ATTENDANCE_SORT_MAP, () -> wrapper.orderByDesc(Attendance::getCheckTime));
        Page<Attendance> page = attendanceMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateAttendanceNames(page.getRecords());
        return page;
    }

    /** 填充考勤记录的关联名称 */
    private void populateAttendanceNames(List<Attendance> list) {
        if (list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(Attendance::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Set<Long> lessonIds = list.stream().map(Attendance::getLessonId).collect(Collectors.toSet());

        // 历史考勤可能引用已软删学员，绕过 @TableLogic 取名
        Map<Long, String> studentNames = loadStudentNamesIncludeDeleted(studentIds);

        // 获取课次信息：绕过 @TableLogic（课次删后仍需显示日期+班级）
        List<ScheduleLesson> lessons;
        if (lessonIds.isEmpty()) {
            lessons = Collections.emptyList();
        } else {
            lessons = scheduleLessonMapper.selectByIdsIncludeDeleted(lessonIds);
        }
        Set<Long> classIds = lessons.stream().map(ScheduleLesson::getClassId).collect(Collectors.toSet());
        // 绕过 @TableLogic 取班级名（班级删后仍需显示）
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

        Map<Long, ScheduleLesson> lessonMap = lessons.stream()
                .collect(Collectors.toMap(ScheduleLesson::getId, sl -> sl, (a, b) -> a));

        for (Attendance a : list) {
            a.setStudentName(studentNames.getOrDefault(a.getStudentId(), ""));
            ScheduleLesson sl = lessonMap.get(a.getLessonId());
            if (sl != null) {
                String cn = classNames.getOrDefault(sl.getClassId(), "");
                a.setLessonInfo(cn + " " + sl.getLessonDate() + " " + sl.getStartTime());
            } else {
                a.setLessonInfo("");
            }
        }
    }

    /** 绕过 @TableLogic 加载学员姓名（包含已软删学员） */
    private Map<Long, String> loadStudentNamesIncludeDeleted(Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) return Collections.emptyMap();
        List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(studentIds);
        return raw.stream()
                .collect(Collectors.toMap(
                        m -> ((Number) m.get("id")).longValue(),
                        m -> (String) m.get("name"),
                        (a, b) -> a));
    }

    @Override
    public Attendance getById(Long id) {
        return attendanceMapper.selectById(id);
    }

    @Override
    public List<ClassStudent> getLessonStudents(Long lessonId) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(lessonId);
        if (lesson == null) throw new BusinessException(404, "课次不存在");
        List<ClassStudent> students = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, lesson.getClassId())
                        .eq(ClassStudent::getStatus, 1));
        Set<Long> studentIds = students.stream().map(ClassStudent::getStudentId).collect(Collectors.toSet());
        Map<Long, String> names = loadStudentNamesIncludeDeleted(studentIds);
        students.forEach(student -> student.setStudentName(names.getOrDefault(student.getStudentId(), "")));
        return students;
    }

    @Override
    public List<Attendance> getByLessonId(Long lessonId) {
        return attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getLessonId, lessonId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reverseDeductByLessonId(Long lessonId, Long operatorId) {
        List<Attendance> records = attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getLessonId, lessonId));
        int reversed = 0;
        for (Attendance record : records) {
            // 仅回冲到课/迟到且实际扣减了的记录
            if ((record.getStatus() == 1 || record.getStatus() == 2)
                    && record.getDeductLessons() != null
                    && record.getDeductLessons().compareTo(BigDecimal.ZERO) > 0) {
                reverseDeduct(record);
                // 标记该记录已回冲：将 deductLessons 置零，防止重复回冲
                record.setDeductLessons(BigDecimal.ZERO);
                attendanceMapper.updateById(record);
                reversed++;
            }
        }
        log.info("调课审批回冲考勤扣减完成：lessonId={}, operatorId={}, 回冲记录数={}",
                lessonId, operatorId, reversed);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Attendance submit(Attendance attendance) {
        // M6+M8 fix: 校验考勤状态非空且为合法值，防止 NPE 和幽灵记录
        if (attendance.getStatus() == null || !java.util.Set.of(1, 2, 3, 4).contains(attendance.getStatus())) {
            throw new BusinessException(400, "无效的考勤状态（仅支持1=到课/2=迟到/3=请假/4=缺勤）");
        }

        // 1. 校验课次
        ScheduleLesson lesson = scheduleLessonMapper.selectById(attendance.getLessonId());
        if (lesson == null) throw new BusinessException(404, "课次不存在");
        if (lesson.getStatus() != 1 && lesson.getStatus() != 2) {
            throw new BusinessException(409, "该课次状态不允许考勤（非待上课/已完成）");
        }

        // 1.1 教师角色校验课次归属（行级数据隔离）
        checkTeacherLessonOwnership(lesson);

        // 1.2 校验学员是否在该班级报名（status=1 表示活跃报名）
        Long enrollmentCount = classStudentMapper.selectCount(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, lesson.getClassId())
                        .eq(ClassStudent::getStudentId, attendance.getStudentId())
                        .eq(ClassStudent::getStatus, 1));
        if (enrollmentCount == 0) {
            throw new BusinessException(409, "该学员未在此班级报名，无法考勤");
        }

        // 2. 去重：同一课次同一学员已有考勤记录 → 先回冲旧扣减，再更新
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getLessonId, attendance.getLessonId())
                        .eq(Attendance::getStudentId, attendance.getStudentId()));
        // Default deductLessons before branching so both update and insert paths have it
        if (attendance.getDeductLessons() == null) {
            attendance.setDeductLessons(BigDecimal.ONE); // 默认扣1课时
        }

        if (existing != null) {
            // 保护请假记录：已有请假审批（status=3, deductLessons=0），不允许覆盖为到课/迟到
            if (existing.getStatus() == 3 && BigDecimal.ZERO.compareTo(
                    existing.getDeductLessons() != null ? existing.getDeductLessons() : BigDecimal.ZERO) == 0
                    && (attendance.getStatus() == 1 || attendance.getStatus() == 2)) {
                throw new BusinessException(409, "该学员已通过请假审批，不可覆盖为到课/迟到；请先撤销请假审批");
            }
            reverseDeduct(existing);
            // 直接更新现有记录
            existing.setStatus(attendance.getStatus());
            existing.setDeductLessons(attendance.getDeductLessons());
            if (attendance.getCheckTime() != null) existing.setCheckTime(attendance.getCheckTime());
            if (attendance.getRemark() != null) existing.setRemark(attendance.getRemark());
            attendanceMapper.updateById(existing);
            attendance.setId(existing.getId());
        } else {
            // 3. 保存考勤
            if (attendance.getCheckTime() == null) attendance.setCheckTime(LocalDateTime.now());
            // H3 fix: 捕获唯一键冲突（并发提交），转为更新路径
            try {
                attendanceMapper.insert(attendance);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                Attendance conflicted = attendanceMapper.selectOne(
                        new LambdaQueryWrapper<Attendance>()
                                .eq(Attendance::getLessonId, attendance.getLessonId())
                                .eq(Attendance::getStudentId, attendance.getStudentId()));
                if (conflicted != null) {
                    reverseDeduct(conflicted);
                    conflicted.setStatus(attendance.getStatus());
                    conflicted.setDeductLessons(attendance.getDeductLessons());
                    if (attendance.getCheckTime() != null) conflicted.setCheckTime(attendance.getCheckTime());
                    if (attendance.getRemark() != null) conflicted.setRemark(attendance.getRemark());
                    attendanceMapper.updateById(conflicted);
                    attendance.setId(conflicted.getId());
                }
            }
        }

        // 4. 计算课时扣减（到课/迟到均扣）
        if (attendance.getStatus() == 1 || attendance.getStatus() == 2) {
            deductLessons(attendance, lesson);
        }

        // 操作日志（M4 fix: 日志失败不应回滚考勤事务）
        try {
            operationLogService.log("考勤管理", "提交考勤（学员=" + nameResolver.getStudentName(attendance.getStudentId())
                    + "，课次id=" + attendance.getLessonId() + "，状态=" + attendance.getStatus() + "）");
        } catch (Exception e) {
            log.warn("考勤操作日志写入失败", e);
        }

        return attendance;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Attendance> batchSubmit(Long lessonId, List<Attendance> list) {
        List<Attendance> results = new java.util.ArrayList<>(list.size());
        for (Attendance a : list) {
            a.setLessonId(lessonId);
            results.add(submit(a));
        }
        populateAttendanceNames(results);
        return results;
    }

    @Override
    public Page<Attendance> pageByStudentId(Long studentId, int pageNum, int pageSize) {
        Page<Attendance> page = attendanceMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getStudentId, studentId)
                        .orderByDesc(Attendance::getCheckTime));
        populateAttendanceNames(page.getRecords());
        return page;
    }

    @Override
    public List<ScheduleLesson> getStudentSchedules(Long studentId) {
        // 1. 查询学员所在的班级
        List<ClassStudent> enrollments = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, studentId)
                        .eq(ClassStudent::getStatus, 1));
        if (enrollments.isEmpty()) return List.of();

        List<Long> classIds = enrollments.stream().map(ClassStudent::getClassId).toList();

        // 2. 一次性查所有班级（含 courseId → 用于回查课程名）
        List<ClassGroup> classes = classGroupMapper.selectBatchIds(classIds);
        Map<Long, ClassGroup> classMap = classes.stream()
                .collect(Collectors.toMap(ClassGroup::getId, c -> c));

        // 3. 一次性查课程名（绕过 @TableLogic 防止已软删课程拿不到）
        Set<Long> courseIds = classes.stream().map(ClassGroup::getCourseId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> courseNameMap = courseIds.isEmpty() ? Collections.emptyMap()
                : classGroupMapper.selectCourseNamesByIdsIncludeDeleted(courseIds).stream()
                .collect(Collectors.toMap(m -> ((Number) m.get("id")).longValue(), m -> (String) m.get("name")));

        // 4. 一次性查教师姓名
        Set<Long> teacherIds = new java.util.HashSet<>();
        for (ClassGroup c : classes) if (c.getTeacherId() != null) teacherIds.add(c.getTeacherId());
        Map<Long, String> teacherNameMap = teacherIds.isEmpty() ? Collections.emptyMap()
                : userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getRealName() != null && !u.getRealName().isBlank() ? u.getRealName() : u.getUsername()));

        // 5. 一次性查教室名
        Set<Long> classroomIds = new java.util.HashSet<>();
        // 暂时教室名留待第 6 步从 schedule_lesson 收集
        // 6. 查询课次
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, classIds)
                        .in(ScheduleLesson::getStatus, 1, 2)
                        .orderByAsc(ScheduleLesson::getLessonDate)
                        .orderByAsc(ScheduleLesson::getStartTime));
        if (lessons.isEmpty()) return List.of();

        for (ScheduleLesson l : lessons) {
            if (l.getClassroomId() != null) classroomIds.add(l.getClassroomId());
        }
        Map<Long, String> classroomNameMap = classroomIds.isEmpty() ? Collections.emptyMap()
                : classroomMapper.selectBatchIds(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));

        // 6.5 查询该学员的请假记录（按日期匹配，排除已拒绝），用于课表显示"请假中/已请假"
        List<LeaveRequest> leaves = leaveRequestMapper.selectList(
                new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getStudentId, studentId)
                        .ne(LeaveRequest::getStatus, 3)
                        .ge(LeaveRequest::getLessonDate, LocalDate.now().minusDays(7)));
        // M6 fix: 使用复合键（scheduleId + lessonDate）构建请假映射，
        // 避免同一日期不同班级/课次的请假相互影响。
        // 已关联具体课次的请假用 "scheduleId_date" 精确匹配；
        // 未关联课次的请假（待审核）用 "null_date" 作为日期级别兜底。
        Map<String, Integer> leaveStatusMap = new java.util.HashMap<>();
        for (LeaveRequest leave : leaves) {
            String key = (leave.getScheduleId() != null
                    ? leave.getScheduleId().toString() : "null") + "_" + leave.getLessonDate();
            leaveStatusMap.putIfAbsent(key, leave.getStatus());
        }

        // 7. 填充关联名称 + 请假状态
        for (ScheduleLesson l : lessons) {
            ClassGroup cg = classMap.get(l.getClassId());
            if (cg != null) {
                l.setClassName(cg.getClassName());
                l.setTeacherName(teacherNameMap.getOrDefault(cg.getTeacherId(), "未指定"));
                // 课程名 + 课程ID（前端按 courseId 分组显示）
                l.setCourseName(courseNameMap.getOrDefault(cg.getCourseId(), ""));
                l.setCourseId(cg.getCourseId());
            }
            l.setClassroomName(classroomNameMap.getOrDefault(l.getClassroomId(), ""));
            // M6 fix: 先按课次ID精确匹配请假状态，再回退到日期级别（兼容未关联课次的待审核请假）
            Integer leaveStatus = leaveStatusMap.get(l.getId() + "_" + l.getLessonDate());
            if (leaveStatus == null) {
                leaveStatus = leaveStatusMap.get("null_" + l.getLessonDate());
            }
            l.setLeaveStatus(leaveStatus);
        }
        return lessons;
    }

    @Override
    public Page<ScheduleLesson> pageTeacherLessons(Long teacherId, int pageNum, int pageSize) {
        return scheduleLessonMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ScheduleLesson>().eq(ScheduleLesson::getTeacherId, teacherId)
                        .in(ScheduleLesson::getStatus, 1, 2)
                        .orderByDesc(ScheduleLesson::getLessonDate));
    }

    @Override
    public List<ScheduleLesson> getTodayLessons(Long teacherId) {
        LocalDate today = LocalDate.now();
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getLessonDate, today)
                        .orderByAsc(ScheduleLesson::getStartTime));
        for (ScheduleLesson l : lessons) {
            l.setClassName(getClassNameSafe(l.getClassId()));
            // 标记是否已迟（当前时间>开始时间 且 状态仍为待上课）
            // Bug #24 fix: 使用99代替2，避免与请假状态语义冲突（2=请假已通过）
            if (l.getStatus() == 1 && l.getStartTime() != null) {
                l.setLeaveStatus(LocalTime.now().isAfter(l.getStartTime()) ? 99 : null);
            }
        }
        return lessons;
    }

    private String getClassNameSafe(Long classId) {
        if (classId == null) return "";
        ClassGroup cg = classGroupMapper.selectById(classId);
        return cg != null ? cg.getClassName() : "";
    }

    @Override
    public void checkTeacherLessonOwnership(Long lessonId) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(lessonId);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        checkTeacherLessonOwnership(lesson);
    }

    // ===== 私有方法 =====

    /** 校验教师角色：当前课次是否属于当前登录教师 */
    private void checkTeacherLessonOwnership(ScheduleLesson lesson) {
        com.pzhu.eduadmin.security.LoginUser loginUser = CurrentUserHolder.get();
        if (loginUser == null) {
            throw new BusinessException(401, "未获取到用户上下文，请重新登录");
        }
        if ("TEACHER".equals(loginUser.getRoleCode())) {
            if (!loginUser.getUserId().equals(lesson.getTeacherId())) {
                throw new BusinessException(403, "该课次不属于您，无法操作");
            }
        }
    }

    /** 回冲旧考勤扣减（到课/迟到扣减均需回冲）*/
    private void reverseDeduct(Attendance old) {
        // M7 fix: 跳过 null 和 <=0 的 deductLessons，防止负数导致回冲时窃取课时
        if ((old.getStatus() != 1 && old.getStatus() != 2)
                || old.getDeductLessons() == null
                || old.getDeductLessons().compareTo(BigDecimal.ZERO) <= 0) return;
        // 找到课时账户并回冲（课次可能已软删，绕过 @TableLogic）
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectByIdsIncludeDeleted(Collections.singleton(old.getLessonId()));
        if (lessons.isEmpty()) return;
        ScheduleLesson lesson = lessons.get(0);
        // M1 fix: 班级也可能已软删，绕过 @TableLogic 查 courseId
        Long courseId = classGroupMapper.selectCourseIdByIdIncludeDeleted(lesson.getClassId());
        if (courseId == null) return;
        LessonAccount account = lessonAccountMapper.selectOne(
                new LambdaQueryWrapper<LessonAccount>()
                        .eq(LessonAccount::getStudentId, old.getStudentId())
                        .eq(LessonAccount::getCourseId, courseId));
        if (account == null) return;

        BigDecimal beforeBalance = account.getRemainingLessons();
        BigDecimal afterBalance = beforeBalance.add(old.getDeductLessons());
        // C2 fix: CAS 中纳入 version 校验并递增，防止与财务模块 updateById 并发时丢失更新
        int rows = lessonAccountMapper.update(null,
                new LambdaUpdateWrapper<LessonAccount>()
                        .eq(LessonAccount::getId, account.getId())
                        .eq(LessonAccount::getRemainingLessons, beforeBalance)
                        .eq(LessonAccount::getVersion, account.getVersion())
                        .set(LessonAccount::getRemainingLessons, afterBalance)
                        .set(LessonAccount::getVersion, account.getVersion() + 1));
        if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");

        LessonFlow flow = new LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(old.getStudentId());
        flow.setLessonId(old.getLessonId());
        flow.setSourceType(3); // 回冲
        flow.setSourceId(old.getId());
        flow.setChangeAmount(old.getDeductLessons());
        flow.setChangeType(1); // 增加（回冲=归还）
        flow.setBeforeBalance(beforeBalance);
        flow.setAfterBalance(afterBalance);
        flow.setRemark("删除考勤回冲");
        lessonFlowMapper.insert(flow);
    }

    /** 扣减课时 */
    private void deductLessons(Attendance attendance, ScheduleLesson lesson) {
        BigDecimal deduct = attendance.getDeductLessons();
        if (BigDecimal.ZERO.compareTo(deduct) >= 0) return;

        LessonAccount account = findAccount(attendance.getStudentId(), lesson.getClassId());
        if (account == null) throw new BusinessException(409, "该学员无课时账户，请先充值");

        BigDecimal before = account.getRemainingLessons();
        if (before.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(409, "课时余额为0，无法扣减，请先续费");
        }

        // 余额不足时最多扣到0
        BigDecimal actualDeduct = before.compareTo(deduct) >= 0 ? deduct : before;
        if (before.compareTo(deduct) < 0) {
            log.warn("考勤扣课时余额不足：studentId={}, lessonId={}, 请求扣减={}, 实际扣减={}",
                    attendance.getStudentId(), attendance.getLessonId(), deduct, actualDeduct);
        }
        // C2 fix: CAS 中纳入 version 校验并递增，防止与财务模块 updateById 并发时丢失更新
        int rows = lessonAccountMapper.update(null,
                new LambdaUpdateWrapper<LessonAccount>()
                        .eq(LessonAccount::getId, account.getId())
                        .eq(LessonAccount::getRemainingLessons, before)
                        .eq(LessonAccount::getVersion, account.getVersion())
                        .set(LessonAccount::getRemainingLessons, before.subtract(actualDeduct))
                        .set(LessonAccount::getVersion, account.getVersion() + 1));
        if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");

        // 写流水
        LessonFlow flow = new LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(attendance.getStudentId());
        flow.setLessonId(attendance.getLessonId());
        flow.setSourceType(2); // 考勤消费
        flow.setSourceId(attendance.getId());
        flow.setChangeAmount(actualDeduct.negate());
        flow.setChangeType(2); // 减少
        flow.setBeforeBalance(before);
        flow.setAfterBalance(before.subtract(actualDeduct));
        flow.setRemark(before.compareTo(deduct) < 0 ? "课时不足，扣至0" : "正常考勤扣课时");
        lessonFlowMapper.insert(flow);
    }

    /** 根据 studentId 和 classId 查找课时账户 */
    private LessonAccount findAccount(Long studentId, Long classId) {
        ClassGroup cg = classGroupMapper.selectById(classId);
        if (cg == null) return null;
        return lessonAccountMapper.selectOne(
                new LambdaQueryWrapper<LessonAccount>()
                        .eq(LessonAccount::getStudentId, studentId)
                        .eq(LessonAccount::getCourseId, cg.getCourseId()));
    }
}
