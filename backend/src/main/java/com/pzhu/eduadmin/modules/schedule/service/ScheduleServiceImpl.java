package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.*;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassroomMapper classroomMapper;
    private final RoomBookingMapper roomBookingMapper;
    private final ScheduleAdjustRequestMapper scheduleAdjustRequestMapper;
    private final ScheduleConflictService scheduleConflictService;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final ClassGroupMapper classGroupMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final NotificationService notificationService;

    private static final Map<String, SFunction<ScheduleLesson, ?>> LESSON_SORT_MAP = Map.of(
            "id", ScheduleLesson::getId,
            "lessonDate", ScheduleLesson::getLessonDate,
            "startTime", ScheduleLesson::getStartTime,
            "status", ScheduleLesson::getStatus
    );
    private static final Map<String, SFunction<Classroom, ?>> ROOM_SORT_MAP = Map.of(
            "id", Classroom::getId,
            "name", Classroom::getName,
            "capacity", Classroom::getCapacity
    );

    @Override
    public Page<ScheduleLesson> pageScheduleLessons(int pageNum, int pageSize,
            String keyword, String sortField, String sortOrder,
            Long courseId, Long classId, Long teacherId, Long classroomId,
            Integer status, LocalDate dateFrom, LocalDate dateTo) {
        LambdaQueryWrapper<ScheduleLesson> wrapper = new LambdaQueryWrapper<>();
        // 过滤：课程（需通过 class_group 中转）
        if (courseId != null) {
            List<ClassGroup> classes = classGroupMapper.selectList(
                    new LambdaQueryWrapper<ClassGroup>().eq(ClassGroup::getCourseId, courseId));
            Set<Long> classIds = classes.stream().map(ClassGroup::getId).collect(Collectors.toSet());
            if (!classIds.isEmpty()) {
                wrapper.in(ScheduleLesson::getClassId, classIds);
            } else {
                wrapper.eq(ScheduleLesson::getId, -1L);
            }
        }
        if (classId != null)     wrapper.eq(ScheduleLesson::getClassId, classId);
        if (teacherId != null)   wrapper.eq(ScheduleLesson::getTeacherId, teacherId);
        if (classroomId != null) wrapper.eq(ScheduleLesson::getClassroomId, classroomId);
        if (status != null)      wrapper.eq(ScheduleLesson::getStatus, status);
        if (dateFrom != null)    wrapper.ge(ScheduleLesson::getLessonDate, dateFrom);
        if (dateTo != null)      wrapper.le(ScheduleLesson::getLessonDate, dateTo);
        QueryHelper.applySort(wrapper, sortField, sortOrder, LESSON_SORT_MAP, () -> wrapper.orderByDesc(ScheduleLesson::getLessonDate));
        Page<ScheduleLesson> page = scheduleLessonMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateScheduleNames(page.getRecords());
        // 关键字过滤（按课程/班级/教师/教室名称，包含中文）
        if (keyword != null && !keyword.isBlank() && !page.getRecords().isEmpty()) {
            String kw = keyword.trim().toLowerCase();
            List<ScheduleLesson> filtered = page.getRecords().stream()
                    .filter(s -> matchesKeyword(s, kw))
                    .collect(Collectors.toList());
            page.setRecords(filtered);
            page.setTotal(filtered.size());
        }
        return page;
    }

    private boolean matchesKeyword(ScheduleLesson s, String kw) {
        return contains(s.getCourseName(), kw)
            || contains(s.getClassName(), kw)
            || contains(s.getTeacherName(), kw)
            || contains(s.getClassroomName(), kw);
    }

    private boolean contains(String v, String kw) {
        return v != null && v.toLowerCase().contains(kw);
    }

    /** 填充排课记录的关联名称（含课程名和课程ID） */
    private void populateScheduleNames(List<ScheduleLesson> list) {
        if (list.isEmpty()) return;
        Set<Long> classIds = list.stream().map(ScheduleLesson::getClassId).collect(Collectors.toSet());
        Set<Long> teacherIds = list.stream().map(ScheduleLesson::getTeacherId).collect(Collectors.toSet());
        Set<Long> roomIds = list.stream().map(ScheduleLesson::getClassroomId).collect(Collectors.toSet());

        Map<Long, String> classNames = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroup::getId, ClassGroup::getClassName));
        Map<Long, String> teacherNames = userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, User::getRealName));
        Map<Long, String> roomNames = classroomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, Classroom::getName));

        // 查询班级 → 课程映射，填充 courseId 和 courseName
        Map<Long, Long> classIdToCourseId = new HashMap<>();
        Set<Long> courseIds = new HashSet<>();
        if (!classIds.isEmpty()) {
            List<ClassGroup> groups = classGroupMapper.selectBatchIds(classIds);
            for (ClassGroup g : groups) {
                classIdToCourseId.put(g.getId(), g.getCourseId());
                if (g.getCourseId() != null) courseIds.add(g.getCourseId());
            }
        }
        Map<Long, String> courseNames = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<Map<String, Object>> raw = courseMapper.selectNamesByIdsIncludeDeleted(courseIds);
            for (Map<String, Object> row : raw) {
                courseNames.put(((Number) row.get("id")).longValue(), (String) row.get("name"));
            }
        }

        for (ScheduleLesson s : list) {
            s.setClassName(classNames.getOrDefault(s.getClassId(), ""));
            s.setTeacherName(teacherNames.getOrDefault(s.getTeacherId(), ""));
            s.setClassroomName(roomNames.getOrDefault(s.getClassroomId(), ""));
            Long cid = classIdToCourseId.get(s.getClassId());
            s.setCourseId(cid);
            s.setCourseName(cid != null ? courseNames.getOrDefault(cid, "") : "");
        }
    }

    @Override
    public ScheduleLesson getLessonById(Long id) {
        return scheduleLessonMapper.selectById(id);
    }

    @Override
    public ScheduleLesson createLesson(ScheduleLesson lesson) {
        List<String> conflicts = scheduleConflictService.checkConflict(lesson);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "排课冲突：" + String.join("；", conflicts));
        }
        scheduleLessonMapper.insert(lesson);
        notifyLessonTeacher(lesson, "CLASS_REMINDER", "新课提醒");
        return lesson;
    }

    private void notifyLessonTeacher(ScheduleLesson lesson, String type, String title) {
        if (lesson.getTeacherId() == null) return;
        try {
            Notification n = new Notification();
            n.setUserId(lesson.getTeacherId());
            n.setType(type);
            n.setTitle(title);
            n.setContent(lesson.getLessonDate() + " " + lesson.getStartTime() + "-" + lesson.getEndTime());
            n.setRelatedId(lesson.getId());
            notificationService.send(lesson.getTeacherId(), n);
        } catch (Exception ignored) {
            // 通知失败不影响主业务
        }
    }

    @Override
    public ScheduleLesson updateLesson(ScheduleLesson lesson) {
        List<String> conflicts = scheduleConflictService.checkConflict(lesson);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "排课冲突：" + String.join("；", conflicts));
        }
        scheduleLessonMapper.updateById(lesson);
        ScheduleLesson updated = scheduleLessonMapper.selectById(lesson.getId());
        notifyLessonTeacher(updated, "SCHEDULE_CHANGE", "课次已更新");
        return updated;
    }

    @Override
    public boolean deleteLesson(Long id) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(id);
        boolean deleted = scheduleLessonMapper.deleteById(id) > 0;
        if (deleted && lesson != null) {
            notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "课次已取消");
        }
        operationLogService.log("排课管理", "删除课次（id=" + id + "）");
        return deleted;
    }

    @Override
    public List<String> checkConflict(ScheduleLesson lesson) {
        return scheduleConflictService.checkConflict(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(List<ScheduleLesson> lessons) {
        // Check intra-batch conflicts (pairwise, before DB insertion)
        for (int i = 0; i < lessons.size(); i++) {
            for (int j = i + 1; j < lessons.size(); j++) {
                ScheduleLesson a = lessons.get(i);
                ScheduleLesson b = lessons.get(j);
                if (hasTimeOverlap(a, b)) {
                    if (a.getTeacherId() != null && a.getTeacherId().equals(b.getTeacherId())) {
                        throw new BusinessException(409, "批次内排课冲突：教师 " + a.getTeacherId() + " 在同一时段有多节课");
                    }
                    if (a.getClassroomId() != null && a.getClassroomId().equals(b.getClassroomId())) {
                        throw new BusinessException(409, "批次内排课冲突：教室在同一时段被占用");
                    }
                    if (a.getClassId() != null && a.getClassId().equals(b.getClassId())) {
                        throw new BusinessException(409, "批次内排课冲突：班级在同一时段有多节课");
                    }
                }
            }
        }

        List<String> allConflicts = new ArrayList<>();
        for (ScheduleLesson lesson : lessons) {
            List<String> conflicts = scheduleConflictService.checkConflict(lesson);
            if (!conflicts.isEmpty()) {
                allConflicts.addAll(conflicts);
            }
        }
        if (!allConflicts.isEmpty()) {
            throw new BusinessException(409, "批量排课存在冲突：" + String.join("；", allConflicts));
        }
        for (ScheduleLesson lesson : lessons) {
            lesson.setStatus(1);
            scheduleLessonMapper.insert(lesson);
        }
    }

    @Override
    public Page<Classroom> pageClassrooms(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<Classroom> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, Classroom::getName, Classroom::getCampus);
        QueryHelper.applySort(wrapper, sortField, sortOrder, ROOM_SORT_MAP, () -> wrapper.orderByDesc(Classroom::getId));
        return classroomMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public Classroom getClassroomById(Long id) {
        return classroomMapper.selectById(id);
    }

    @Override
    public Classroom createClassroom(Classroom classroom) {
        if (classroom.getName() == null || classroom.getName().isBlank()) {
            throw new BusinessException(400, "教室名称不能为空");
        }
        if (classroom.getCapacity() == null || classroom.getCapacity() <= 0) {
            throw new BusinessException(400, "教室容量必须大于0");
        }
        classroomMapper.insert(classroom);
        return classroom;
    }

    @Override
    public Classroom updateClassroom(Classroom classroom) {
        classroomMapper.updateById(classroom);
        return classroomMapper.selectById(classroom.getId());
    }

    @Override
    public boolean deleteClassroom(Long id) {
        operationLogService.log("教室管理", "删除教室（id=" + id + "）");
        return classroomMapper.deleteById(id) > 0;
    }

    @Override
    public Page<RoomBooking> pageRoomBookings(int pageNum, int pageSize) {
        return roomBookingMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public RoomBooking createRoomBooking(RoomBooking booking) {
        roomBookingMapper.insert(booking);
        return booking;
    }

    @Override
    public Page<ScheduleAdjustRequest> pageAdjustRequests(int pageNum, int pageSize) {
        return scheduleAdjustRequestMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    public Page<ScheduleAdjustRequest> pageTeacherAdjustRequests(Long teacherId, int pageNum, int pageSize) {
        return scheduleAdjustRequestMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ScheduleAdjustRequest>().eq(ScheduleAdjustRequest::getApplicantId, teacherId));
    }

    @Override
    public ScheduleAdjustRequest createAdjustRequest(ScheduleAdjustRequest request) {
        scheduleAdjustRequestMapper.insert(request);
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleAdjustRequest auditAdjustRequest(Long id, Integer status, Long auditorId, String remark) {
        ScheduleAdjustRequest request = scheduleAdjustRequestMapper.selectById(id);
        if (request == null) throw new BusinessException(404, "调课申请不存在");
        if (request.getStatus() != null && request.getStatus() != 1) {
            throw new BusinessException(409, "该调课申请已处理");
        }
        if (request.getExpectTime() == null) throw new BusinessException(400, "调课申请缺少期望时间");
        // CAS 原子更新：防止并发审核
        LambdaUpdateWrapper<ScheduleAdjustRequest> updateWrapper = new LambdaUpdateWrapper<ScheduleAdjustRequest>()
                .eq(ScheduleAdjustRequest::getId, id)
                .eq(ScheduleAdjustRequest::getStatus, 1)
                .set(ScheduleAdjustRequest::getStatus, status)
                .set(ScheduleAdjustRequest::getAuditorId, auditorId)
                .set(ScheduleAdjustRequest::getAuditRemark, remark);
        int updated = scheduleAdjustRequestMapper.update(null, updateWrapper);
        if (updated == 0) {
            throw new BusinessException(409, "该调课申请已被处理，请刷新后重试");
        }
        request.setStatus(status);
        request.setAuditorId(auditorId);
        request.setAuditRemark(remark);

        // 审核通过：落地新课次
        if (status == 2) {
            ScheduleLesson oldLesson = scheduleLessonMapper.selectById(request.getLessonId());
            if (oldLesson == null) throw new BusinessException(404, "原课次不存在或已删除，无法完成调课");
            oldLesson.setStatus(4); // 已调课
            scheduleLessonMapper.updateById(oldLesson);

            ScheduleLesson newLesson = new ScheduleLesson();
            newLesson.setClassId(oldLesson.getClassId());
            newLesson.setTeacherId(oldLesson.getTeacherId());
            newLesson.setClassroomId(oldLesson.getClassroomId());
            newLesson.setLessonDate(request.getExpectTime().toLocalDate());
            newLesson.setStartTime(request.getExpectTime().toLocalTime());
            newLesson.setEndTime(request.getExpectTime().toLocalTime().plusMinutes(60));
            newLesson.setStatus(1);
            newLesson.setSourceLessonId(oldLesson.getId());

            List<String> conflicts = scheduleConflictService.checkConflict(newLesson);
            if (!conflicts.isEmpty()) {
                throw new BusinessException(409, "调课冲突：" + String.join("；", conflicts));
            }
            scheduleLessonMapper.insert(newLesson);
        }

        // 操作日志
        operationLogService.log("排课管理", (status == 2 ? "审核通过调课申请" : "驳回调课申请") + "（id=" + id + "）");

        return request;
    }

    /** 判断两个课次是否在同一天且时间段有重叠（复用 ScheduleConflictServiceImpl 的重叠公式） */
    private boolean hasTimeOverlap(ScheduleLesson a, ScheduleLesson b) {
        if (a.getLessonDate() == null || b.getLessonDate() == null) return false;
        if (!a.getLessonDate().equals(b.getLessonDate())) return false;
        if (a.getStartTime() == null || a.getEndTime() == null) return false;
        if (b.getStartTime() == null || b.getEndTime() == null) return false;
        return a.getStartTime().isBefore(b.getEndTime()) && a.getEndTime().isAfter(b.getStartTime());
    }
}
