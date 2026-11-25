package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.dto.TeacherDetailVO;
import com.pzhu.eduadmin.modules.user.dto.TeacherListItemVO;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TeacherAdminServiceImpl implements TeacherAdminService {

    private static final String ROLE_TEACHER = "TEACHER";

    /** 历史任教最多展示的班级数，避免教龄长的教师一次拉出上百个班 */
    private static final int HISTORY_CLASS_LIMIT = 20;

    private final UserMapper userMapper;
    private final ClassGroupMapper classGroupMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final CourseMapper courseMapper;
    private final UserService userService;
    private final OperationLogService operationLogService;

    @Override
    public PageResult<TeacherListItemVO> pageTeachers(int pageNum, int pageSize, String keyword, Integer statusFilter) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getRoleCode, ROLE_TEACHER);

        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(User::getRealName, kw)
                    .or().like(User::getUsername, kw)
                    .or().like(User::getPhone, kw));
        }
        if (statusFilter != null) {
            wrapper.eq(User::getStatus, statusFilter);
        }
        wrapper.orderByDesc(User::getId);

        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<User> records = page.getRecords();

        List<TeacherListItemVO> items;
        if (records.isEmpty()) {
            items = Collections.emptyList();
        } else {
            userService.fillTeachersSpecialties(records);
            items = records.stream().map(this::toListItem).collect(Collectors.toList());
        }

        PageResult<TeacherListItemVO> result = PageResult.of(page.getTotal(), items);
        result.setPageNum(page.getCurrent());
        result.setPageSize(page.getSize());
        return result;
    }

    @Override
    public TeacherDetailVO getDetail(Long teacherId) {
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(404, "教师不存在");
        }
        if (!ROLE_TEACHER.equals(teacher.getRoleCode())) {
            throw new BusinessException(400, "该用户不是教师");
        }

        TeacherDetailVO vo = new TeacherDetailVO();
        vo.setId(teacher.getId());
        vo.setUsername(teacher.getUsername());
        vo.setRealName(teacher.getRealName());
        vo.setPhone(teacher.getPhone());
        vo.setHireDate(teacher.getHireDate());
        vo.setLeaveDate(teacher.getLeaveDate());
        vo.setStatus(teacher.getStatus());
        vo.setMustChangePassword(teacher.getMustChangePassword());

        String[] status = describeStatus(teacher.getStatus(), teacher.getLeaveDate());
        vo.setStatusLabel(status[0]);
        vo.setStatusType(status[1]);

        userService.fillTeachersSpecialties(List.of(teacher));
        vo.setSpecialtyCourseIds(teacher.getSpecialtyCourseIds());
        vo.setSpecialties(teacher.getSpecialties());

        // ---- 当前任教 ----
        List<ClassGroup> currentGroups = classGroupMapper.selectList(
                new LambdaQueryWrapper<ClassGroup>()
                        .eq(ClassGroup::getTeacherId, teacherId)
                        .eq(ClassGroup::getStatus, 1));
        Set<Long> currentClassIds = currentGroups.stream()
                .map(ClassGroup::getId).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> currentCourseNames = loadCourseNames(currentGroups.stream()
                .map(ClassGroup::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));
        Map<Long, Integer> studentCounts = countActiveStudents(currentClassIds);

        for (ClassGroup group : currentGroups) {
            TeacherDetailVO.TeachingClassVO item = new TeacherDetailVO.TeachingClassVO();
            item.setClassId(group.getId());
            item.setClassName(group.getClassName());
            item.setCourseName(currentCourseNames.get(group.getCourseId()));
            item.setStudentCount(studentCounts.getOrDefault(group.getId(), 0));
            vo.getCurrentClasses().add(item);
        }

        // ---- 历史任教 ----
        // class_group.teacher_id 只保存"当前"任教，换师后历史即丢失，
        // 因此只能从 schedule_lesson 中 status=2（已完成）的课次回溯。
        List<ScheduleLesson> finished = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .select(ScheduleLesson::getClassId, ScheduleLesson::getLessonDate)
                        .eq(ScheduleLesson::getTeacherId, teacherId)
                        .eq(ScheduleLesson::getStatus, 2)
                        .orderByDesc(ScheduleLesson::getLessonDate));

        Map<Long, LocalDate> lastLessonDate = new LinkedHashMap<>();
        Map<Long, Integer> lessonCount = new HashMap<>();
        for (ScheduleLesson lesson : finished) {
            Long classId = lesson.getClassId();
            if (classId == null || currentClassIds.contains(classId)) {
                continue;
            }
            lessonCount.merge(classId, 1, Integer::sum);
            // finished 已按日期倒序，putIfAbsent 保留的就是最近一次
            lastLessonDate.putIfAbsent(classId, lesson.getLessonDate());
        }

        List<Long> historyIds = new ArrayList<>(lastLessonDate.keySet());
        if (historyIds.size() > HISTORY_CLASS_LIMIT) {
            historyIds = historyIds.subList(0, HISTORY_CLASS_LIMIT);
        }
        if (!historyIds.isEmpty()) {
            // selectBatchIds 受 @TableLogic 影响：已删除的班级查不到，
            // 此时仅用 "班级#id" 兜底展示，不影响课次数与最近授课日。
            Map<Long, ClassGroup> groupMap = classGroupMapper.selectBatchIds(historyIds).stream()
                    .collect(Collectors.toMap(ClassGroup::getId, g -> g, (a, b) -> a));
            Map<Long, String> historyCourseNames = loadCourseNames(groupMap.values().stream()
                    .map(ClassGroup::getCourseId).filter(Objects::nonNull).collect(Collectors.toSet()));

            for (Long classId : historyIds) {
                ClassGroup group = groupMap.get(classId);
                TeacherDetailVO.TeachingClassVO item = new TeacherDetailVO.TeachingClassVO();
                item.setClassId(classId);
                item.setClassName(group != null ? group.getClassName() : "班级#" + classId);
                item.setCourseName(group != null ? historyCourseNames.get(group.getCourseId()) : null);
                item.setLessonCount(lessonCount.getOrDefault(classId, 0));
                item.setLastLessonDate(lastLessonDate.get(classId));
                vo.getHistoryClasses().add(item);
            }
        }

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resign(Long teacherId, LocalDate leaveDate) {
        User teacher = userMapper.selectById(teacherId);
        if (teacher == null) {
            throw new BusinessException(404, "教师不存在");
        }
        if (!ROLE_TEACHER.equals(teacher.getRoleCode())) {
            throw new BusinessException(400, "该用户不是教师");
        }
        if (!Integer.valueOf(1).equals(teacher.getStatus())) {
            throw new BusinessException(400, "该教师已离职或已停用，无需重复操作");
        }

        // 一次 UPDATE 完成三件事，避免"只改了状态 / 只写了离职日"的半成品数据。
        // version + 1 使该教师已签发的 token 立即失效（JwtInterceptor 会比对 version）。
        int updated = userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, teacherId)
                .eq(User::getStatus, 1)
                .set(User::getLeaveDate, leaveDate)
                .set(User::getStatus, 0)
                .setSql("version = version + 1"));

        if (updated == 0) {
            throw new BusinessException(409, "教师状态已变更，请刷新后重试");
        }

        operationLogService.log("教师管理",
                "办理离职（教师=" + teacher.getRealName() + "，离职日=" + leaveDate + "）",
                "USER", teacherId);
    }

    // ==================== 内部方法 ====================

    private TeacherListItemVO toListItem(User user) {
        TeacherListItemVO vo = new TeacherListItemVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setRealName(user.getRealName());
        vo.setPhone(user.getPhone());
        vo.setHireDate(user.getHireDate());
        vo.setLeaveDate(user.getLeaveDate());
        vo.setStatus(user.getStatus());
        vo.setMustChangePassword(user.getMustChangePassword());
        vo.setSpecialtyCourseIds(user.getSpecialtyCourseIds());
        vo.setSpecialties(user.getSpecialties());

        String[] status = describeStatus(user.getStatus(), user.getLeaveDate());
        vo.setStatusLabel(status[0]);
        vo.setStatusType(status[1]);
        return vo;
    }

    /**
     * 读时计算教师状态，返回 { 文案, ElTag 类型 }。
     * <p>判定必须用 equals 而不是 {@code status != 1}：库里存在 status 为 NULL 的脏数据，
     * 用 {@code != 1} 会把它当成"已离职"或"在职"而漏判。参见 JwtInterceptor 的同类注释。
     */
    private String[] describeStatus(Integer status, LocalDate leaveDate) {
        LocalDate today = LocalDate.now();
        if (!Integer.valueOf(1).equals(status)) {
            return new String[]{"已离职", "info"};
        }
        if (leaveDate == null) {
            return new String[]{"在职", "success"};
        }
        if (leaveDate.isAfter(today)) {
            return new String[]{"将于 " + leaveDate + " 离职", "warning"};
        }
        return new String[]{"已过离职日未停用", "danger"};
    }

    private Map<Long, String> loadCourseNames(Set<Long> courseIds) {
        if (courseIds == null || courseIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return courseMapper.selectBatchIds(courseIds).stream()
                .filter(c -> c.getName() != null)
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));
    }

    private Map<Long, Integer> countActiveStudents(Set<Long> classIds) {
        if (classIds == null || classIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return classStudentMapper.selectList(
                        new LambdaQueryWrapper<ClassStudent>()
                                .select(ClassStudent::getClassId)
                                .in(ClassStudent::getClassId, classIds)
                                .eq(ClassStudent::getStatus, 1))
                .stream()
                .collect(Collectors.groupingBy(ClassStudent::getClassId, Collectors.summingInt(x -> 1)));
    }
}
