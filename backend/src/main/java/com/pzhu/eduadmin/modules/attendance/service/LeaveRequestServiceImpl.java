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
        leaveRequestMapper.insert(lr);
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
                // 已有考勤记录，不重复创建
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
            attendanceMapper.insert(attendance);
        }

        // 更新请假记录的 scheduleId（取第一个匹配课次）
        if (!lessons.isEmpty()) {
            lr.setScheduleId(lessons.get(0).getId());
            leaveRequestMapper.updateById(lr);
        }
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
}
