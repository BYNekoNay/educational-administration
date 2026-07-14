package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper;
import com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AttendanceServiceImpl implements AttendanceService {

    private final AttendanceMapper attendanceMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ClassGroupMapper classGroupMapper;
    private final LessonAccountMapper lessonAccountMapper;
    private final LessonFlowMapper lessonFlowMapper;
    private final OperationLogMapper operationLogMapper;
    private final StudentMapper studentMapper;

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
        Set<Long> studentIds = list.stream().map(Attendance::getStudentId).collect(Collectors.toSet());
        Set<Long> lessonIds = list.stream().map(Attendance::getLessonId).collect(Collectors.toSet());

        Map<Long, String> studentNames = studentMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(Student::getId, Student::getName));

        // 获取课次信息：课次 -> 班级 -> 班级名称 + 日期 + 时间
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectBatchIds(lessonIds);
        Set<Long> classIds = lessons.stream().map(ScheduleLesson::getClassId).collect(Collectors.toSet());
        Map<Long, String> classNames = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroup::getId, ClassGroup::getClassName));

        Map<Long, ScheduleLesson> lessonMap = lessons.stream()
                .collect(Collectors.toMap(ScheduleLesson::getId, sl -> sl));

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

        // 2. 去重：同一课次同一学员已有考勤记录 → 先回冲旧扣减，再更新
        Attendance existing = attendanceMapper.selectOne(
                new LambdaQueryWrapper<Attendance>()
                        .eq(Attendance::getLessonId, attendance.getLessonId())
                        .eq(Attendance::getStudentId, attendance.getStudentId()));
        if (existing != null) {
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
            if (attendance.getDeductLessons() == null) {
                attendance.setDeductLessons(BigDecimal.ONE); // 默认扣1课时
            }
            attendanceMapper.insert(attendance);
        }

        // 4. 计算课时扣减（到课才扣）
        if (attendance.getStatus() == 1) {
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
        for (Attendance a : list) {
            a.setLessonId(lessonId);
            submit(a);
        }
        return list;
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
        // 查询学员所在的班级
        List<ClassStudent> enrollments = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, studentId)
                        .eq(ClassStudent::getStatus, 1));
        if (enrollments.isEmpty()) return List.of();

        List<Long> classIds = enrollments.stream().map(ClassStudent::getClassId).toList();
        // 查询这些班级的课次
        return scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, classIds)
                        .in(ScheduleLesson::getStatus, 1, 2)
                        .orderByAsc(ScheduleLesson::getLessonDate)
                        .orderByAsc(ScheduleLesson::getStartTime));
    }

    @Override
    public Page<ScheduleLesson> pageTeacherLessons(Long teacherId, int pageNum, int pageSize) {
        return scheduleLessonMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ScheduleLesson>().eq(ScheduleLesson::getTeacherId, teacherId)
                        .in(ScheduleLesson::getStatus, 1, 2)
                        .orderByDesc(ScheduleLesson::getLessonDate));
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
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }

    /** 回冲旧考勤扣减 */
    private void reverseDeduct(Attendance old) {
        if (old.getStatus() != 1 || BigDecimal.ZERO.compareTo(old.getDeductLessons()) == 0) return;
        // 找到课时账户并回冲
        ScheduleLesson lesson = scheduleLessonMapper.selectById(old.getLessonId());
        if (lesson == null) return;
        LessonAccount account = findAccount(old.getStudentId(), lesson.getClassId());
        if (account == null) return;

        account.setRemainingLessons(account.getRemainingLessons().add(old.getDeductLessons()));
        account.setVersion(account.getVersion() + 1);
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
        account.setRemainingLessons(before.subtract(actualDeduct));
        account.setVersion(account.getVersion() + 1);
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
