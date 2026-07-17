package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
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
    private final OperationLogMapper operationLogMapper;
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
        return classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, lesson.getClassId()));
    }

    @Override
    public List<Attendance> getByLessonId(Long lessonId) {
        return attendanceMapper.selectList(
                new LambdaQueryWrapper<Attendance>().eq(Attendance::getLessonId, lessonId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Attendance submit(Attendance attendance) {
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
            attendanceMapper.insert(attendance);
        }

        // 4. 计算课时扣减（到课/迟到均扣）
        if (attendance.getStatus() == 1 || attendance.getStatus() == 2) {
            deductLessons(attendance, lesson);
        }

        // 操作日志
        logOperation("考勤管理", "提交考勤(lessonId=" + attendance.getLessonId()
                + ",studentId=" + attendance.getStudentId() + ",status=" + attendance.getStatus() + ")");

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
        Map<LocalDate, Integer> leaveStatusMap = leaves.stream()
                .collect(Collectors.toMap(LeaveRequest::getLessonDate, LeaveRequest::getStatus,
                        (a, b) -> a)); // 同一天多条请假取第一条

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
            // 请假状态（null=无请假, 1=请假中, 2=已通过）
            l.setLeaveStatus(leaveStatusMap.get(l.getLessonDate()));
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
        if (loginUser == null) return;
        if ("TEACHER".equals(loginUser.getRoleCode())) {
            if (!loginUser.getUserId().equals(lesson.getTeacherId())) {
                throw new BusinessException(403, "该课次不属于您，无法操作");
            }
        }
    }

    /** 记录操作日志 */
    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser operator = CurrentUserHolder.get();
        log.setOperatorId(operator != null ? operator.getUserId() : 0L);
        log.setModule(module);
        log.setOperation(operation);
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);
    }

    /** 回冲旧考勤扣减（到课/迟到扣减均需回冲）*/
    private void reverseDeduct(Attendance old) {
        if ((old.getStatus() != 1 && old.getStatus() != 2)
                || old.getDeductLessons() == null
                || BigDecimal.ZERO.compareTo(old.getDeductLessons()) == 0) return;
        // 找到课时账户并回冲（课次可能已软删，绕过 @TableLogic）
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectByIdsIncludeDeleted(Collections.singleton(old.getLessonId()));
        if (lessons.isEmpty()) return;
        ScheduleLesson lesson = lessons.get(0);
        LessonAccount account = findAccount(old.getStudentId(), lesson.getClassId());
        if (account == null) return;

        account.setRemainingLessons(account.getRemainingLessons().add(old.getDeductLessons()));
        account.setTotalLessons(account.getTotalLessons().add(old.getDeductLessons())); // 同步回冲 totalLessons
        int rows = lessonAccountMapper.updateById(account);
        if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");

        LessonFlow flow = new LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(old.getStudentId());
        flow.setLessonId(old.getLessonId());
        flow.setSourceType(3); // 回冲
        flow.setSourceId(old.getId());
        flow.setChangeAmount(old.getDeductLessons());
        flow.setChangeType(1); // 增加（回冲=归还）
        flow.setBeforeBalance(account.getRemainingLessons().subtract(old.getDeductLessons()));
        flow.setAfterBalance(account.getRemainingLessons());
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
        account.setRemainingLessons(before.subtract(actualDeduct));
        int rows = lessonAccountMapper.updateById(account);
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
        flow.setAfterBalance(account.getRemainingLessons());
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
