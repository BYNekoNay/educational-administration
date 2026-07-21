package com.pzhu.eduadmin.modules.attendance.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
import com.pzhu.eduadmin.modules.attendance.entity.LeaveRequest;
import com.pzhu.eduadmin.modules.attendance.mapper.AttendanceMapper;
import com.pzhu.eduadmin.modules.attendance.mapper.LeaveRequestMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LeaveRequestServiceImpl implements LeaveRequestService {

    private final LeaveRequestMapper leaveRequestMapper;
    private final StudentMapper studentMapper;
    private final ParentStudentMapper parentStudentMapper;
    private final AttendanceMapper attendanceMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassStudentMapper classStudentMapper;
    private final com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper classGroupMapper;
    private final com.pzhu.eduadmin.modules.finance.mapper.LessonAccountMapper lessonAccountMapper;
    private final com.pzhu.eduadmin.modules.finance.mapper.LessonFlowMapper lessonFlowMapper;

    @Override
    public List<LeaveRequest> getByParent(Long parentUserId, Long studentId) {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<LeaveRequest>()
                .eq(LeaveRequest::getParentUserId, parentUserId)
                .eq(studentId != null, LeaveRequest::getStudentId, studentId)
                .orderByDesc(LeaveRequest::getCreateTime);
        List<LeaveRequest> list = leaveRequestMapper.selectList(wrapper);
        populateNames(list);
        return list;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest submitLeaveRequest(Long parentUserId, Long studentId, LocalDate lessonDate, String reason) {
        // 校验学员是否存在
        Student student = studentMapper.selectById(studentId);
        if (student == null) {
            throw new BusinessException(404, "学员不存在");
        }

        // 校验日期不能是过去的
        if (lessonDate.isBefore(LocalDate.now())) {
            throw new BusinessException(400, "不可为过去的日期提交请假申请");
        }

        // Issue #30: 校验家长与学员的绑定关系
        Long bindingCount = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId)
                        .eq(ParentStudent::getStudentId, studentId));
        if (bindingCount == null || bindingCount == 0) {
            throw new BusinessException(403, "您与该学员没有绑定关系，无法提交请假申请");
        }

        // 去重：同一学员同一天已有待审核/已通过的请假
        Long existingCount = leaveRequestMapper.selectCount(
                new LambdaQueryWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getStudentId, studentId)
                        .eq(LeaveRequest::getLessonDate, lessonDate)
                        .in(LeaveRequest::getStatus, 1, 2));
        if (existingCount != null && existingCount > 0) {
            throw new BusinessException(409, "该学员在同一天已有请假申请，请勿重复提交");
        }

        LeaveRequest lr = new LeaveRequest();
        lr.setStudentId(studentId);
        lr.setParentUserId(parentUserId);
        lr.setLessonDate(lessonDate);
        lr.setReason(reason);
        lr.setStatus(1); // pending
        // H5 fix: 捕获唯一键冲突（并发提交竞态）
        try {
            leaveRequestMapper.insert(lr);
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(409, "该学员在同一天已有请假申请，请勿重复提交");
        }
        return lr;
    }

    @Override
    public Page<LeaveRequest> pageAll(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            // keyword search: match against student name or reason
            // First find student ids matching keyword
            List<Student> matchedStudents = studentMapper.selectList(
                    new LambdaQueryWrapper<Student>().like(Student::getName, keyword));
            List<Long> studentIds = matchedStudents.stream().map(Student::getId).toList();

            wrapper.and(w -> {
                w.like(LeaveRequest::getReason, keyword);
                if (!studentIds.isEmpty()) {
                    w.or().in(LeaveRequest::getStudentId, studentIds);
                }
            });
        }
        wrapper.orderByDesc(LeaveRequest::getCreateTime);
        Page<LeaveRequest> page = leaveRequestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateNames(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest audit(Long id, Integer status, Long auditUserId, String remark) {
        if (status != 2 && status != 3) {
            throw new BusinessException(400, "审核状态只能为2(通过)或3(驳回)");
        }
        LeaveRequest lr = leaveRequestMapper.selectById(id);
        if (lr == null) {
            throw new BusinessException(404, "请假记录不存在");
        }
        if (lr.getStatus() != 1) {
            throw new BusinessException(409, "该请假申请已审核，不可重复操作");
        }

        // CAS 原子更新防止并发审核
        int updated = leaveRequestMapper.update(null,
                new LambdaUpdateWrapper<LeaveRequest>()
                        .eq(LeaveRequest::getId, id)
                        .eq(LeaveRequest::getStatus, 1)
                        .set(LeaveRequest::getStatus, status)
                        .set(LeaveRequest::getAuditUserId, auditUserId)
                        .set(LeaveRequest::getAuditRemark, remark));
        if (updated == 0) {
            throw new BusinessException(409, "该请假申请已被处理，请刷新后重试");
        }
        // 同步更新内存对象状态
        lr.setStatus(status);
        lr.setAuditUserId(auditUserId);
        lr.setAuditRemark(remark);

        // 审核通过时自动创建考勤记录（status=3 请假, deductLessons=0）
        if (status == 2) {
            createLeaveAttendance(lr);
        }

        return lr;
    }

    /**
     * 审核通过时：查找学员在请假日期对应的课次，创建请假考勤记录。
     */
    private void createLeaveAttendance(LeaveRequest lr) {
        // 查找学员所在的班级
        List<ClassStudent> enrollments = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getStudentId, lr.getStudentId())
                        .eq(ClassStudent::getStatus, 1));
        if (enrollments.isEmpty()) {
            return;
        }

        List<Long> classIds = enrollments.stream().map(ClassStudent::getClassId).toList();

        // 查找请假日期匹配的所有课次（支持学员同一天在多个班级有课）
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .in(ScheduleLesson::getClassId, classIds)
                        .eq(ScheduleLesson::getLessonDate, lr.getLessonDate())
                        .in(ScheduleLesson::getStatus, 1, 2));

        if (lessons.isEmpty()) {
            // 当天没有排课，仍然批准请假但不创建考勤记录
            return;
        }

        for (ScheduleLesson lesson : lessons) {
            // 检查是否已存在该学员在该课次的考勤记录
            Attendance existing = attendanceMapper.selectOne(
                    new LambdaQueryWrapper<Attendance>()
                            .eq(Attendance::getLessonId, lesson.getId())
                            .eq(Attendance::getStudentId, lr.getStudentId()));
            if (existing != null) {
                // H4 fix: 若已有出勤/迟到记录且有扣减，需反向回冲后改为请假状态
                if ((existing.getStatus() == 1 || existing.getStatus() == 2)
                        && existing.getDeductLessons() != null
                        && existing.getDeductLessons().compareTo(BigDecimal.ZERO) > 0) {
                    reverseAttendanceDeduction(existing, lesson);
                    existing.setStatus(3);
                    existing.setDeductLessons(BigDecimal.ZERO);
                    existing.setRemark("请假审批覆盖原出勤记录");
                    attendanceMapper.updateById(existing);
                }
                continue;
            }

            // 创建请假考勤记录
            Attendance attendance = new Attendance();
            attendance.setStudentId(lr.getStudentId());
            attendance.setLessonId(lesson.getId());
            attendance.setStatus(3); // 3=请假
            attendance.setDeductLessons(BigDecimal.ZERO);
            attendance.setCheckTime(LocalDateTime.now());
            attendance.setRemark("请假审批自动创建");
            // M7 fix: 并发审批时可能重复插入，捕获唯一键冲突
            try {
                attendanceMapper.insert(attendance);
            } catch (DuplicateKeyException e) {
                // 已有记录被并发创建，跳过
            }
        }

        // 更新请假记录的 scheduleId（取第一个匹配课次）
        if (!lessons.isEmpty()) {
            lr.setScheduleId(lessons.get(0).getId());
            leaveRequestMapper.updateById(lr);
        }
    }

    @Override
    public Page<LeaveRequest> pageByTeacher(Long teacherId, int pageNum, int pageSize) {
        // 查找该教师教授的所有班级ID
        List<Long> classIds = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .select(ScheduleLesson::getClassId))
                .stream().map(ScheduleLesson::getClassId).distinct().collect(Collectors.toList());

        // 教师没有任何课次，返回空页
        if (classIds.isEmpty()) {
            Page<LeaveRequest> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            return emptyPage;
        }

        // 查找这些班级中的所有在班学员ID
        List<Long> studentIds = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .in(ClassStudent::getClassId, classIds)
                        .eq(ClassStudent::getStatus, 1)
                        .select(ClassStudent::getStudentId))
                .stream().map(ClassStudent::getStudentId).distinct().collect(Collectors.toList());

        // 班级中没有学员，返回空页
        if (studentIds.isEmpty()) {
            Page<LeaveRequest> emptyPage = new Page<>(pageNum, pageSize);
            emptyPage.setRecords(Collections.emptyList());
            emptyPage.setTotal(0);
            return emptyPage;
        }

        // 按学员ID查询请假申请
        LambdaQueryWrapper<LeaveRequest> wrapper = new LambdaQueryWrapper<LeaveRequest>()
                .in(LeaveRequest::getStudentId, studentIds)
                .orderByDesc(LeaveRequest::getCreateTime);
        Page<LeaveRequest> page = leaveRequestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateNames(page.getRecords());
        return page;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LeaveRequest auditByTeacher(Long id, Integer status, Long auditUserId, String remark) {
        LeaveRequest lr = leaveRequestMapper.selectById(id);
        if (lr == null) throw new BusinessException(404, "请假记录不存在");

        // 校验教师是否有权审批该学员的请假申请
        boolean authorized = false;

        // 快速路径：如果 scheduleId 已填充（已审批过的记录），直接校验课次归属
        if (lr.getScheduleId() != null) {
            ScheduleLesson lesson = scheduleLessonMapper.selectById(lr.getScheduleId());
            if (lesson != null && auditUserId.equals(lesson.getTeacherId())) {
                authorized = true;
            }
        }

        // 通用路径：通过 学员 -> 班级 -> 教师 链路校验
        if (!authorized) {
            List<ClassStudent> enrollments = classStudentMapper.selectList(
                    new LambdaQueryWrapper<ClassStudent>()
                            .eq(ClassStudent::getStudentId, lr.getStudentId())
                            .eq(ClassStudent::getStatus, 1));
            if (!enrollments.isEmpty()) {
                List<Long> classIds = enrollments.stream()
                        .map(ClassStudent::getClassId).collect(Collectors.toList());
                // M5 fix: 限定到请假日期对应的课次，防止教师审批无关班级的请假
                // M6 fix: 仅匹配有效状态的课次（排除已调课/已取消）
                Long matchCount = scheduleLessonMapper.selectCount(
                        new LambdaQueryWrapper<ScheduleLesson>()
                                .in(ScheduleLesson::getClassId, classIds)
                                .eq(ScheduleLesson::getTeacherId, auditUserId)
                                .eq(ScheduleLesson::getLessonDate, lr.getLessonDate())
                                .in(ScheduleLesson::getStatus, 1, 2));
                if (matchCount != null && matchCount > 0) {
                    authorized = true;
                }
            }
        }

        if (!authorized) {
            throw new BusinessException(403, "该学员不属于您的班级，无法审批");
        }

        return audit(id, status, auditUserId, remark);
    }

    /** 填充请假记录的关联名称 */
    private void populateNames(List<LeaveRequest> list) {
        if (list == null || list.isEmpty()) return;
        Set<Long> studentIds = list.stream().map(LeaveRequest::getStudentId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        // 历史请假可能引用已软删学员，绕过 @TableLogic 取名
        Map<Long, String> studentNames;
        if (studentIds.isEmpty()) {
            studentNames = Collections.emptyMap();
        } else {
            List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(studentIds);
            studentNames = raw.stream()
                    .collect(Collectors.toMap(
                            m -> ((Number) m.get("id")).longValue(),
                            m -> (String) m.get("name"),
                            (a, b) -> a));
        }
        for (LeaveRequest lr : list) {
            lr.setStudentName(studentNames.getOrDefault(lr.getStudentId(), ""));
        }
    }

    /**
     * H4 fix: 回冲已有出勤记录的课时扣减（请假审批覆盖出勤时调用）。
     */
    private void reverseAttendanceDeduction(Attendance attendance, ScheduleLesson lesson) {
        com.pzhu.eduadmin.modules.course.entity.ClassGroup cg = classGroupMapper.selectById(lesson.getClassId());
        if (cg == null) return;
        com.pzhu.eduadmin.modules.finance.entity.LessonAccount account = lessonAccountMapper.selectOne(
                new LambdaQueryWrapper<com.pzhu.eduadmin.modules.finance.entity.LessonAccount>()
                        .eq(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getStudentId, attendance.getStudentId())
                        .eq(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getCourseId, cg.getCourseId()));
        if (account == null) return;

        BigDecimal before = account.getRemainingLessons();
        BigDecimal after = before.add(attendance.getDeductLessons());
        int rows = lessonAccountMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<com.pzhu.eduadmin.modules.finance.entity.LessonAccount>()
                        .eq(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getId, account.getId())
                        .eq(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getRemainingLessons, before)
                        .eq(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getVersion, account.getVersion())
                        .set(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getRemainingLessons, after)
                        .set(com.pzhu.eduadmin.modules.finance.entity.LessonAccount::getVersion, account.getVersion() + 1));
        if (rows == 0) throw new BusinessException(409, "课时账户更新冲突，请重试");

        com.pzhu.eduadmin.modules.finance.entity.LessonFlow flow = new com.pzhu.eduadmin.modules.finance.entity.LessonFlow();
        flow.setAccountId(account.getId());
        flow.setStudentId(attendance.getStudentId());
        flow.setLessonId(attendance.getLessonId());
        flow.setSourceType(3);
        flow.setSourceId(attendance.getId());
        flow.setChangeAmount(attendance.getDeductLessons());
        flow.setChangeType(1);
        flow.setBeforeBalance(before);
        flow.setAfterBalance(after);
        flow.setRemark("请假审批回冲出勤扣减");
        lessonFlowMapper.insert(flow);
    }
}
