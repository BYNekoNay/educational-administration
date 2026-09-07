package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.entity.Period;
import com.pzhu.eduadmin.modules.schedule.dto.AdjustRequestVO;
import com.pzhu.eduadmin.modules.schedule.dto.AutoScheduleRequest;
import com.pzhu.eduadmin.modules.schedule.mapper.*;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.schedule.dto.QuickAdjustRequest;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
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
    private final AttendanceService attendanceService;
    private final PeriodMapper periodMapper;
    private final ClassStudentMapper classStudentMapper;
    private final ParentStudentMapper parentStudentMapper;

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

        // Bug #9 fix: 关键字过滤下推到数据库查询，避免分页后内存过滤导致结果不正确
        if (keyword != null && !keyword.isBlank()) {
            // L3 fix: 转义 LIKE 元字符（与 QueryHelper.applyKeyword 一致），防止用户输入的 %/_ 被当作通配符
            String kw = keyword.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
            // 按教师姓名匹配
            List<Long> matchedTeacherIds = userMapper.selectList(
                    new LambdaQueryWrapper<User>().like(User::getRealName, kw))
                    .stream().map(User::getId).collect(Collectors.toList());
            // 按班级名称匹配
            List<Long> matchedClassIds = classGroupMapper.selectList(
                    new LambdaQueryWrapper<ClassGroup>().like(ClassGroup::getClassName, kw))
                    .stream().map(ClassGroup::getId).collect(Collectors.toList());
            // 按教室名称匹配
            List<Long> matchedRoomIds = classroomMapper.selectList(
                    new LambdaQueryWrapper<Classroom>().like(Classroom::getName, kw))
                    .stream().map(Classroom::getId).collect(Collectors.toList());
            // 按课程名称匹配（通过 class_group 中转）
            List<Long> matchedCourseIds = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>().like(Course::getName, kw))
                    .stream().map(Course::getId).collect(Collectors.toList());
            if (!matchedCourseIds.isEmpty()) {
                List<Long> courseClassIds = classGroupMapper.selectList(
                        new LambdaQueryWrapper<ClassGroup>().in(ClassGroup::getCourseId, matchedCourseIds))
                        .stream().map(ClassGroup::getId).collect(Collectors.toList());
                matchedClassIds.addAll(courseClassIds);
            }

            // 所有维度均无匹配时直接返回空页
            if (matchedTeacherIds.isEmpty() && matchedClassIds.isEmpty() && matchedRoomIds.isEmpty()) {
                Page<ScheduleLesson> emptyPage = new Page<>(pageNum, pageSize);
                emptyPage.setRecords(new ArrayList<>());
                emptyPage.setTotal(0);
                return emptyPage;
            }

            // 以 OR 条件拼入主查询
            final List<Long> teacherIds = matchedTeacherIds;
            final List<Long> classIds2 = matchedClassIds;
            final List<Long> roomIds = matchedRoomIds;
            wrapper.and(w -> {
                boolean needOr = false;
                if (!teacherIds.isEmpty()) {
                    w.in(ScheduleLesson::getTeacherId, teacherIds);
                    needOr = true;
                }
                if (!classIds2.isEmpty()) {
                    if (needOr) w.or();
                    w.in(ScheduleLesson::getClassId, classIds2);
                    needOr = true;
                }
                if (!roomIds.isEmpty()) {
                    if (needOr) w.or();
                    w.in(ScheduleLesson::getClassroomId, roomIds);
                }
            });
        }

        QueryHelper.applySort(wrapper, sortField, sortOrder, LESSON_SORT_MAP, () -> wrapper.orderByDesc(ScheduleLesson::getLessonDate));
        Page<ScheduleLesson> page = scheduleLessonMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateScheduleNames(page.getRecords());
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
                .collect(Collectors.toMap(ClassGroup::getId, c -> c.getClassName() != null ? c.getClassName() : "", (a, b) -> a));
        Map<Long, String> teacherNames = userMapper.selectBatchIds(teacherIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u.getRealName() != null ? u.getRealName() : (u.getUsername() != null ? u.getUsername() : "")));
        Map<Long, String> roomNames = classroomMapper.selectBatchIds(roomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, r -> r.getName() != null ? r.getName() : "", (a, b) -> a));

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

        // 批量填充时段名称
        Set<Long> periodIds = list.stream().map(ScheduleLesson::getPeriodId)
                .filter(id -> id != null).collect(Collectors.toSet());
        if (!periodIds.isEmpty()) {
            Map<Long, String> periodNameMap = periodMapper.selectBatchIds(periodIds).stream()
                    .collect(Collectors.toMap(Period::getId, Period::getName, (a, b) -> a));
            for (ScheduleLesson s : list) {
                if (s.getPeriodId() != null) {
                    s.setPeriodName(periodNameMap.getOrDefault(s.getPeriodId(), ""));
                }
            }
        }
    }

    @Override
    public ScheduleLesson getLessonById(Long id) {
        return scheduleLessonMapper.selectById(id);
    }

    @Override
    public ScheduleLesson createLesson(ScheduleLesson lesson) {
        // Bug #28 fix: 关键字段为空时冲突检测会被跳过，导致插入无效课次
        if (lesson.getLessonDate() == null || lesson.getStartTime() == null || lesson.getEndTime() == null) {
            throw new BusinessException(400, "课次日期和起止时间不能为空");
        }
        // M5 fix: 校验起止时间合法性
        if (!lesson.getEndTime().isAfter(lesson.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        // L6 fix: 校验引用的教室存在（无外键约束，冲突检测对未知教室恒通过，会留下悬空引用）
        validateClassroomExists(lesson.getClassroomId());
        List<String> conflicts = scheduleConflictService.checkConflict(lesson);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "排课冲突：" + String.join("；", conflicts));
        }
        scheduleLessonMapper.insert(lesson);
        notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "新增课次安排");
        return lesson;
    }

    /** L6 fix: 校验课次引用的教室存在（null 放行；selectById 遵循 @TableLogic）；A3#1: 且须为启用状态 */
    private void validateClassroomExists(Long classroomId) {
        if (classroomId == null) return;
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null) {
            throw new BusinessException(404, "教室不存在(id=" + classroomId + ")");
        }
        // A3#1 fix: 停用教室(status!=1)不可排课，与 autoSchedule/createRoomBooking 的 status=1 不变式对齐
        if (!Integer.valueOf(1).equals(classroom.getStatus())) {
            throw new BusinessException(409, "该教室已停用，无法排课(id=" + classroomId + ")");
        }
    }

    private void notifyLessonTeacher(ScheduleLesson lesson, String type, String title) {
        if (lesson.getTeacherId() == null) return;
        final Long teacherId = lesson.getTeacherId();
        final Notification n = new Notification();
        n.setUserId(teacherId);
        n.setType(type);
        n.setTitle(title);
        n.setContent(lesson.getLessonDate() + " " + lesson.getStartTime() + "-" + lesson.getEndTime());
        n.setRelatedId(lesson.getId());
        // M fix: send() 是 @Async，会立即在独立线程入库并推送 SSE。若在事务提交前派发，
        // 一旦事务回滚（如批量排课后续插入失败），已发出的通知成为幽灵通知（课次不存在但教师已收到）。
        // 因此事务内延迟到 afterCommit 再派发；无事务时直接派发。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        notificationService.send(teacherId, n);
                    } catch (Exception ignored) {
                        // 通知失败不影响主业务
                    }
                }
            });
        } else {
            try {
                notificationService.send(teacherId, n);
            } catch (Exception ignored) {
                // 通知失败不影响主业务
            }
        }
    }

    @Override
    public ScheduleLesson updateLesson(ScheduleLesson lesson) {
        // H6 fix: 加载现有记录并合并非 null 字段，确保部分更新时冲突检测使用完整数据
        ScheduleLesson existing = scheduleLessonMapper.selectById(lesson.getId());
        if (existing == null) throw new BusinessException(404, "课次不存在或已删除");
        // M3 fix: 仅允许编辑待上课（status=1）课次。已完成（status=2）课次可能已产生考勤扣减，
        // 其冲销路径按课次当前 classId 定位课时账户，若此时修改 classId/teacherId 会导致
        // 后续请假/调课冲销回退到错误课程的账户（资金串账）。状态变更请走调课/取消专用流程。
        if (existing.getStatus() != null && existing.getStatus() != 1) {
            throw new BusinessException(409, "仅待上课课次可编辑，已完成/已调课课次请使用调课/取消流程");
        }
        if (lesson.getLessonDate() != null) existing.setLessonDate(lesson.getLessonDate());
        if (lesson.getStartTime() != null) existing.setStartTime(lesson.getStartTime());
        if (lesson.getEndTime() != null) existing.setEndTime(lesson.getEndTime());
        if (lesson.getTeacherId() != null) existing.setTeacherId(lesson.getTeacherId());
        if (lesson.getClassroomId() != null) existing.setClassroomId(lesson.getClassroomId());
        if (lesson.getClassId() != null) existing.setClassId(lesson.getClassId());
        // M fix: 不接受客户端覆盖 status。状态变更必须走调课/取消专用流程（含考勤冲销），
        // 直接置 status=4/2 会绕过冲销导致课时账户不一致，非法值还会污染 status IN (1,2) 过滤。

        // L6 fix: 变更教室时校验新教室存在（仅当请求携带 classroomId，避免误伤引用已删除教室的历史课次）
        if (lesson.getClassroomId() != null) {
            validateClassroomExists(lesson.getClassroomId());
        }

        // M5 fix: 校验合并后的起止时间合法性
        if (existing.getStartTime() != null && existing.getEndTime() != null
                && !existing.getEndTime().isAfter(existing.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }

        List<String> conflicts = scheduleConflictService.checkConflict(existing);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "排课冲突：" + String.join("；", conflicts));
        }
        scheduleLessonMapper.updateById(existing);
        ScheduleLesson updated = scheduleLessonMapper.selectById(lesson.getId());
        notifyLessonTeacher(updated, "SCHEDULE_CHANGE", "课次已更新");
        return updated;
    }

    @Override
    public boolean deleteLesson(Long id) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        // High fix: 仅允许删除待上课（status=1）的课次。已完成（status=2）等状态的课次
        // 可能已产生考勤扣减，直接删除会导致课时账户永久不一致（扣减无法回滚）。
        if (lesson.getStatus() != null && lesson.getStatus() != 1) {
            throw new BusinessException(409, "该课次已非待上课状态，无法删除（如需调整请使用调课/取消功能）");
        }
        boolean deleted = scheduleLessonMapper.deleteById(id) > 0;
        if (deleted) {
            notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "课次已取消");
        }
        // Low fix: 日志失败不应影响删除结果（删除已提交）
        try {
            operationLogService.log("排课管理", "删除课次（id=" + id + "）");
        } catch (Exception ignored) {
            // 日志异常不阻断主流程
        }
        return deleted;
    }

    @Override
    public List<String> checkConflict(ScheduleLesson lesson) {
        return scheduleConflictService.checkConflict(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(List<ScheduleLesson> lessons) {
        // Bug fix: 批量创建同样需要逐条校验，避免空时间或非法时间段绕过冲突检测被插入
        for (int idx = 0; idx < lessons.size(); idx++) {
            ScheduleLesson lesson = lessons.get(idx);
            if (lesson.getLessonDate() == null || lesson.getStartTime() == null || lesson.getEndTime() == null) {
                throw new BusinessException(400, "第 " + (idx + 1) + " 节课次日期和起止时间不能为空");
            }
            if (!lesson.getEndTime().isAfter(lesson.getStartTime())) {
                throw new BusinessException(400, "第 " + (idx + 1) + " 节课次结束时间必须晚于开始时间");
            }
        }

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
            notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "新增课次安排");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ScheduleLesson> autoSchedule(AutoScheduleRequest request) {
        if (scheduleLessonMapper.lockAutoSchedule() == null) {
            throw new BusinessException(500, "智能排课事务锁未初始化");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new BusinessException(400, "排课结束日期不能早于开始日期");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }

        ClassGroup classGroup = classGroupMapper.selectById(request.getClassId());
        if (classGroup == null) throw new BusinessException(404, "班级不存在");
        if (Integer.valueOf(0).equals(classGroup.getStatus())) {
            throw new BusinessException(409, "停用班级不能排课");
        }

        User teacher = userMapper.selectById(request.getTeacherId());
        if (teacher == null || !"TEACHER".equals(teacher.getRoleCode())) {
            throw new BusinessException(404, "授课教师不存在");
        }
        if (!Integer.valueOf(1).equals(teacher.getStatus())) {
            throw new BusinessException(409, "授课教师已停用");
        }

        List<Classroom> rooms;
        if (request.getClassroomId() != null) {
            Classroom room = classroomMapper.selectById(request.getClassroomId());
            rooms = room == null ? List.of() : List.of(room);
        } else {
            rooms = classroomMapper.selectList(
                    new LambdaQueryWrapper<Classroom>()
                            .eq(Classroom::getStatus, 1)
                            .orderByAsc(Classroom::getCapacity));
        }
        int requiredCapacity = classGroup.getMaxStudentCount() == null ? 0 : classGroup.getMaxStudentCount();
        rooms = rooms.stream()
                .filter(room -> Integer.valueOf(1).equals(room.getStatus()))
                .filter(room -> room.getCapacity() != null && room.getCapacity() >= requiredCapacity)
                .toList();
        if (rooms.isEmpty()) throw new BusinessException(409, "没有容量满足要求的可用教室");

        Set<Integer> weekdays = request.getWeekdays() == null || request.getWeekdays().isEmpty()
                ? Set.of(request.getStartDate().getDayOfWeek().getValue())
                : new HashSet<>(request.getWeekdays());
        List<ScheduleLesson> generated = new ArrayList<>();
        LocalDate date = request.getStartDate();
        while (!date.isAfter(request.getEndDate()) && generated.size() < request.getLessonCount()) {
            if (weekdays.contains(date.getDayOfWeek().getValue())) {
                for (Classroom room : rooms) {
                    ScheduleLesson candidate = new ScheduleLesson();
                    candidate.setClassId(request.getClassId());
                    candidate.setTeacherId(request.getTeacherId());
                    candidate.setClassroomId(room.getId());
                    candidate.setLessonDate(date);
                    candidate.setStartTime(request.getStartTime());
                    candidate.setEndTime(request.getEndTime());
                    candidate.setStatus(1);
                    if (scheduleConflictService.checkConflict(candidate).isEmpty()) {
                        generated.add(candidate);
                        break;
                    }
                }
            }
            date = date.plusDays(1);
        }

        if (generated.size() < request.getLessonCount()) {
            throw new BusinessException(409, "指定日期范围内无法生成足够的无冲突课次");
        }
        batchCreate(generated);
        return generated;
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
        // L5 fix: 校验存在性 + 名称/容量合法性（与 createClassroom 对齐），仅校验请求携带的字段
        Classroom existing = classroomMapper.selectById(classroom.getId());
        if (existing == null) {
            throw new BusinessException(404, "教室不存在");
        }
        if (classroom.getName() != null && classroom.getName().isBlank()) {
            throw new BusinessException(400, "教室名称不能为空");
        }
        if (classroom.getCapacity() != null && classroom.getCapacity() <= 0) {
            throw new BusinessException(400, "教室容量必须大于0");
        }
        classroomMapper.updateById(classroom);
        return classroomMapper.selectById(classroom.getId());
    }

    @Override
    public boolean deleteClassroom(Long id) {
        // Bug #27 fix: 检查该教室是否有未来排课，防止删除后产生孤立记录
        long futureLessons = scheduleLessonMapper.selectCount(new LambdaQueryWrapper<ScheduleLesson>()
                .eq(ScheduleLesson::getClassroomId, id)
                .ge(ScheduleLesson::getLessonDate, LocalDate.now())
                .in(ScheduleLesson::getStatus, 1, 2));
        if (futureLessons > 0) {
            throw new BusinessException(409, "该教室有未来排课，无法删除");
        }
        // L fix: 未来预约也需拦截，否则删除教室后孤立预约仍按 classroomId 阻塞排课冲突检测
        long futureBookings = roomBookingMapper.selectCount(new LambdaQueryWrapper<RoomBooking>()
                .eq(RoomBooking::getClassroomId, id)
                .gt(RoomBooking::getEndTime, LocalDateTime.now()));
        if (futureBookings > 0) {
            throw new BusinessException(409, "该教室有未来预约，无法删除");
        }
        try {
            operationLogService.log("教室管理", "删除教室（id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }
        return classroomMapper.deleteById(id) > 0;
    }

    @Override
    public Page<RoomBooking> pageRoomBookings(int pageNum, int pageSize) {
        return roomBookingMapper.selectPage(new Page<>(pageNum, pageSize), new LambdaQueryWrapper<>());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomBooking createRoomBooking(RoomBooking booking) {
        // C3 fix: 输入校验
        if (booking.getClassroomId() == null) throw new BusinessException(400, "教室ID不能为空");
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BusinessException(400, "预约开始/结束时间不能为空");
        }
        if (!booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        // L4 fix: 校验教室存在（selectById 遵循 @TableLogic，已删除教室视为不存在），防止预约引用孤立教室
        Classroom classroom = classroomMapper.selectById(booking.getClassroomId());
        if (classroom == null) {
            throw new BusinessException(404, "教室不存在");
        }
        // L fix: 停用教室（status!=1）不可预约（autoSchedule 仅用 status=1 教室，此处需对齐）
        if (!Integer.valueOf(1).equals(classroom.getStatus())) {
            throw new BusinessException(409, "该教室已停用，无法预约");
        }
        // L fix: 拒绝已结束的历史预约，避免污染冲突检测
        if (booking.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "预约结束时间不能早于当前时间");
        }
        // Bug #26 fix: 检查同一教室是否存在时间段重叠的预约
        long conflicts = roomBookingMapper.selectCount(new LambdaQueryWrapper<RoomBooking>()
                .eq(RoomBooking::getClassroomId, booking.getClassroomId())
                .lt(RoomBooking::getStartTime, booking.getEndTime())
                .gt(RoomBooking::getEndTime, booking.getStartTime()));
        if (conflicts > 0) {
            throw new BusinessException(409, "该时段教室已被预约");
        }
        // M8 fix: 预约还需对称地检查已排课次——排课侧会查预约冲突，但预约侧此前只查预约，
        // 可创建与已有课次重叠的预约导致教室双重占用。按日期范围取该教室课次再逐条判时间重叠。
        LocalDate bookStartDate = booking.getStartTime().toLocalDate();
        LocalDate bookEndDate = booking.getEndTime().toLocalDate();
        List<ScheduleLesson> roomLessons = scheduleLessonMapper.selectList(new LambdaQueryWrapper<ScheduleLesson>()
                .eq(ScheduleLesson::getClassroomId, booking.getClassroomId())
                .between(ScheduleLesson::getLessonDate, bookStartDate, bookEndDate)
                .in(ScheduleLesson::getStatus, 1, 2));
        for (ScheduleLesson l : roomLessons) {
            if (l.getLessonDate() == null || l.getStartTime() == null || l.getEndTime() == null) continue;
            LocalDateTime lessonStart = l.getLessonDate().atTime(l.getStartTime());
            LocalDateTime lessonEnd = l.getLessonDate().atTime(l.getEndTime());
            if (lessonStart.isBefore(booking.getEndTime()) && lessonEnd.isAfter(booking.getStartTime())) {
                throw new BusinessException(409, "该时段教室已有排课，无法预约");
            }
        }
        roomBookingMapper.insert(booking);
        // C3 fix: 插入后二次校验（防止并发 TOCTOU），若冲突则回滚
        long postConflicts = roomBookingMapper.selectCount(new LambdaQueryWrapper<RoomBooking>()
                .eq(RoomBooking::getClassroomId, booking.getClassroomId())
                .lt(RoomBooking::getStartTime, booking.getEndTime())
                .gt(RoomBooking::getEndTime, booking.getStartTime()));
        if (postConflicts > 1) {
            throw new BusinessException(409, "该时段教室已被预约（并发冲突）");
        }
        return booking;
    }

    @Override
    public Page<AdjustRequestVO> pageAdjustRequests(int pageNum, int pageSize, Integer status) {
        LambdaQueryWrapper<ScheduleAdjustRequest> wrapper = new LambdaQueryWrapper<ScheduleAdjustRequest>()
                .orderByDesc(ScheduleAdjustRequest::getId);
        if (status != null) wrapper.eq(ScheduleAdjustRequest::getStatus, status);
        Page<ScheduleAdjustRequest> page = scheduleAdjustRequestMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        return buildAdjustRequestVOPage(page, pageNum, pageSize);
    }

    @Override
    public Page<AdjustRequestVO> pageTeacherAdjustRequests(Long userId, int pageNum, int pageSize) {
        Page<ScheduleAdjustRequest> page = scheduleAdjustRequestMapper.selectPage(
                new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<ScheduleAdjustRequest>()
                        .eq(ScheduleAdjustRequest::getApplicantId, userId)
                        .orderByDesc(ScheduleAdjustRequest::getId));
        return buildAdjustRequestVOPage(page, pageNum, pageSize);
    }

    /**
     * 将 ScheduleAdjustRequest 分页转为富化的 AdjustRequestVO 分页（批量 JOIN 课次+班级+课程+时段）
     */
    private Page<AdjustRequestVO> buildAdjustRequestVOPage(Page<ScheduleAdjustRequest> page, int pageNum, int pageSize) {
        Page<AdjustRequestVO> result = new Page<>(pageNum, pageSize, page.getTotal());
        if (page.getRecords().isEmpty()) return result;

        // 批量查询关联的课次
        Set<Long> lessonIds = page.getRecords().stream()
                .map(ScheduleAdjustRequest::getLessonId).collect(Collectors.toSet());
        List<ScheduleLesson> lessons = scheduleLessonMapper.selectBatchIds(lessonIds);
        Map<Long, ScheduleLesson> lessonMap = lessons.stream()
                .collect(Collectors.toMap(ScheduleLesson::getId, l -> l, (a, b) -> a));

        // 查班级
        Set<Long> classIds = lessons.stream().map(ScheduleLesson::getClassId).collect(Collectors.toSet());
        Map<Long, ClassGroup> classMap = classGroupMapper.selectBatchIds(classIds).stream()
                .collect(Collectors.toMap(ClassGroup::getId, c -> c, (a, b) -> a));

        // 查课程
        Set<Long> courseIds = classMap.values().stream().map(ClassGroup::getCourseId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> courseNameMap = new HashMap<>();
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseMapper.selectBatchIds(courseIds);
            for (Course c : courses) courseNameMap.put(c.getId(), c.getName());
        }

        // 查时段
        Set<Long> periodIds = lessons.stream().map(ScheduleLesson::getPeriodId).filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, String> periodNameMap = new HashMap<>();
        if (!periodIds.isEmpty()) {
            List<Period> periods = periodMapper.selectBatchIds(periodIds);
            for (Period p : periods) periodNameMap.put(p.getId(), p.getName());
        }

        // 查教师名
        Set<Long> teacherIds = lessons.stream().map(ScheduleLesson::getTeacherId).collect(Collectors.toSet());
        Map<Long, String> teacherNameMap = new HashMap<>();
        if (!teacherIds.isEmpty()) {
            List<User> teachers = userMapper.selectBatchIds(teacherIds);
            for (User u : teachers) teacherNameMap.put(u.getId(), u.getRealName() != null ? u.getRealName() : u.getUsername());
        }

        // 查教室名
        Set<Long> roomIds = lessons.stream().map(ScheduleLesson::getClassroomId).collect(Collectors.toSet());
        Map<Long, String> roomNameMap = new HashMap<>();
        if (!roomIds.isEmpty()) {
            List<Classroom> rooms = classroomMapper.selectBatchIds(roomIds);
            for (Classroom r : rooms) roomNameMap.put(r.getId(), r.getName());
        }

        // 组装 VO
        List<AdjustRequestVO> vos = page.getRecords().stream().map(req -> {
            AdjustRequestVO vo = new AdjustRequestVO();
            vo.setId(req.getId());
            vo.setLessonId(req.getLessonId());
            vo.setExpectTime(req.getExpectTime());
            vo.setReason(req.getReason());
            vo.setStatus(req.getStatus());
            vo.setAuditRemark(req.getAuditRemark());
            vo.setCreateTime(req.getCreateTime());

            ScheduleLesson lesson = lessonMap.get(req.getLessonId());
            if (lesson != null) {
                vo.setLessonDate(lesson.getLessonDate());
                vo.setStartTime(lesson.getStartTime());
                vo.setEndTime(lesson.getEndTime());
                vo.setPeriodId(lesson.getPeriodId());
                vo.setPeriodCount(lesson.getPeriodCount());
                vo.setPeriodName(periodNameMap.getOrDefault(lesson.getPeriodId(), ""));
                vo.setTeacherName(teacherNameMap.getOrDefault(lesson.getTeacherId(), ""));
                vo.setClassroomName(roomNameMap.getOrDefault(lesson.getClassroomId(), ""));

                ClassGroup cg = classMap.get(lesson.getClassId());
                if (cg != null) {
                    vo.setClassName(cg.getClassName());
                    vo.setCourseName(courseNameMap.getOrDefault(cg.getCourseId(), ""));
                }
            }
            return vo;
        }).collect(Collectors.toList());

        result.setRecords(vos);
        return result;
    }

    @Override
    public ScheduleAdjustRequest createAdjustRequest(ScheduleAdjustRequest request) {
        // M9 fix: 校验课次存在
        if (request.getLessonId() == null) {
            throw new BusinessException(400, "课次ID不能为空");
        }
        ScheduleLesson lesson = scheduleLessonMapper.selectById(request.getLessonId());
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        // Medium fix: 教师角色校验课次归属，防止教师对他人课次发起调课申请
        // （与 TeacherAttendanceController.createMyAdjustRequest 的归属校验保持一致）
        com.pzhu.eduadmin.security.LoginUser loginUser = com.pzhu.eduadmin.security.CurrentUserHolder.get();
        if (loginUser != null && "TEACHER".equals(loginUser.getRoleCode())
                && !loginUser.getUserId().equals(lesson.getTeacherId())) {
            throw new BusinessException(403, "该课次不属于您，无法发起调课申请");
        }
        // L fix: 仅待上课（status=1）课次可发起调课。已完成/已调课课次的申请永远无法通过，徒增死请求且误导用户。
        if (!Integer.valueOf(1).equals(lesson.getStatus())) {
            throw new BusinessException(409, "仅待上课课次可发起调课申请");
        }
        // L fix: 同一课次已有待审批调课申请时拒绝重复提交
        Long pendingAdjustCount = scheduleAdjustRequestMapper.selectCount(new LambdaQueryWrapper<ScheduleAdjustRequest>()
                .eq(ScheduleAdjustRequest::getLessonId, request.getLessonId())
                .eq(ScheduleAdjustRequest::getStatus, 1));
        if (pendingAdjustCount > 0) {
            throw new BusinessException(409, "该课次已有待审批的调课申请，请勿重复提交");
        }
        scheduleAdjustRequestMapper.insert(request);
        return request;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleAdjustRequest auditAdjustRequest(Long id, Integer status, Long auditorId, String remark) {
        // H8 fix: 校验审核状态只接受 2（通过）或 3（驳回）
        if (status == null || (status != 2 && status != 3)) {
            throw new BusinessException(400, "审核状态只能为2（通过）或3（驳回）");
        }
        ScheduleAdjustRequest request = scheduleAdjustRequestMapper.selectById(id);
        if (request == null) throw new BusinessException(404, "调课申请不存在");
        if (request.getStatus() != null && request.getStatus() != 1) {
            throw new BusinessException(409, "该调课申请已处理");
        }
        // Medium fix: expectTime 校验移至审核通过分支（驳回无需期望时间），
        // 避免缺少 expectTime 的申请既不能通过也不能驳回而永久卡住
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
            // Medium fix: 仅审核通过时需要期望时间（用于生成新课次）
            if (request.getExpectTime() == null) {
                throw new BusinessException(400, "调课申请缺少期望时间，无法通过");
            }
            ScheduleLesson oldLesson = scheduleLessonMapper.selectById(request.getLessonId());
            if (oldLesson == null) throw new BusinessException(404, "原课次不存在或已删除，无法完成调课");
            // M fix: 原课次必须仍为待上课（status=1）。否则同一课次的多笔待审批调课申请可被重复通过，
            // 第二次审批时原课次已是 status=4，置 4 为无操作却又插入第二个替换课次（幽灵课次，统计/考勤重复计入）。
            if (!Integer.valueOf(1).equals(oldLesson.getStatus())) {
                throw new BusinessException(409, "原课次已非待上课状态，无法调课");
            }
            // M fix: 原子地将原课次 status 1→4（CAS）。仅对 request 行加 CAS 不足以防止同一课次的多笔
            // 待审批申请被并发通过——两个事务都快照读到 status=1、都通过上方守卫、都 insert 替换课次（幽灵课次，
            // 统计/考勤重复计入）。改用课次行 CAS，确保只有一个并发审批能成功翻转状态。
            int lessonFlipped = scheduleLessonMapper.update(null, new LambdaUpdateWrapper<ScheduleLesson>()
                    .eq(ScheduleLesson::getId, oldLesson.getId())
                    .eq(ScheduleLesson::getStatus, 1)
                    .set(ScheduleLesson::getStatus, 4));
            if (lessonFlipped == 0) {
                throw new BusinessException(409, "原课次已被处理，无法调课");
            }
            oldLesson.setStatus(4); // 同步内存对象状态供后续逻辑使用

            ScheduleLesson newLesson = new ScheduleLesson();
            newLesson.setClassId(oldLesson.getClassId());
            newLesson.setTeacherId(oldLesson.getTeacherId());
            newLesson.setClassroomId(oldLesson.getClassroomId());
            newLesson.setLessonDate(request.getExpectTime().toLocalDate());
            // 课节时段继承（调课不改变时段）
            newLesson.setPeriodId(oldLesson.getPeriodId());
            newLesson.setPeriodCount(oldLesson.getPeriodCount());
            // M fix: 历史遗留 status=1 课次可能缺起止时间，Duration.between 会 NPE(500)，
            // 且因方法带事务会使该调课申请永久卡死。提前校验给出友好错误。
            if (oldLesson.getStartTime() == null || oldLesson.getEndTime() == null) {
                throw new BusinessException(409, "原课次缺少上课时间，无法调课");
            }
            Duration lessonDuration = Duration.between(oldLesson.getStartTime(), oldLesson.getEndTime());
            newLesson.setStartTime(request.getExpectTime().toLocalTime());
            java.time.LocalTime newEndTime = request.getExpectTime().toLocalTime().plus(lessonDuration);
            // H7 fix: 校验调课后不跨午夜，否则冲突检测公式失效
            if (!newEndTime.isAfter(request.getExpectTime().toLocalTime())) {
                throw new BusinessException(400, "调课后的课次不能跨越午夜（结束时间早于开始时间）");
            }
            newLesson.setEndTime(newEndTime);
            newLesson.setStatus(1);

            // H1 fix: sourceLessonId 语义说明 ——
            // sourceLessonId 始终指向被替换的原课次，但根据新旧课次的 teacherId 是否一致，
            // 其业务含义不同：
            //   - 调课（reschedule）：newTeacherId == oldTeacherId，同一教师换时间/教室；
            //   - 代课（substitute）：newTeacherId != oldTeacherId，由另一位教师接替该课次。
            // 当前实现中新课次继承原课次教师（即调课场景），若未来支持指定代课教师，
            // 只需在此处设置不同的 teacherId，sourceLessonId 的关联逻辑无需变更。
            boolean isSubstitute = newLesson.getTeacherId() != null
                    && oldLesson.getTeacherId() != null
                    && !newLesson.getTeacherId().equals(oldLesson.getTeacherId());
            // 分类标记：isSubstitute=true 为代课，false 为调课（当前逻辑固定为调课）
            newLesson.setSourceLessonId(oldLesson.getId());

            // L fix: 调课生成的新课次继承原教室，需复核教室仍存在（原课次创建后教室可能被软删除），
            // 否则替换课次静默引用孤立教室，且冲突检测对未知教室恒通过。
            validateClassroomExists(newLesson.getClassroomId());

            List<String> conflicts = scheduleConflictService.checkConflict(newLesson);
            if (!conflicts.isEmpty()) {
                throw new BusinessException(409, (isSubstitute ? "代课" : "调课") + "冲突：" + String.join("；", conflicts));
            }
            scheduleLessonMapper.insert(newLesson);

            // C1 fix: 调课审批通过后，回冲原课次已扣减的考勤课时
            attendanceService.reverseDeductByLessonId(request.getLessonId(), auditorId);
        }

        // 操作日志
        try {
            operationLogService.log("排课管理", (status == 2 ? "审核通过调课申请" : "驳回调课申请") + "（id=" + id + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return request;
    }

    // ============ P1 快速调课（教务拖拽） ============

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleLesson quickAdjustLesson(Long id, QuickAdjustRequest request) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在或已删除");
        }
        // 仅待上课课次可快速调课；已完成/已取消/已调课需走对应专用流程
        if (!Integer.valueOf(1).equals(lesson.getStatus())) {
            throw new BusinessException(409, "仅待上课课次可快速调课，已完成/已取消/已调课课次请使用调课或取消流程");
        }
        if (lesson.getLessonDate() == null || lesson.getStartTime() == null || lesson.getEndTime() == null) {
            throw new BusinessException(409, "课次缺少完整上课时间，无法调课");
        }
        LocalDate today = LocalDate.now();
        // 已过去的课次不可调整
        if (lesson.getLessonDate().isBefore(today)) {
            throw new BusinessException(400, "已开始的过去课次不可调课");
        }
        // 今天且当前时间已过开始时间 → 已开始，禁止拖拽
        if (lesson.getLessonDate().equals(today) && !lesson.getStartTime().isAfter(LocalTime.now())) {
            throw new BusinessException(409, "该课次已开始，无法快速调课");
        }
        // 入参合法性
        if (request.getLessonDate() == null || request.getStartTime() == null || request.getEndTime() == null) {
            throw new BusinessException(400, "目标日期和起止时间不能为空");
        }
        if (!request.getEndTime().isAfter(request.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        if (request.getLessonDate().isBefore(today)) {
            throw new BusinessException(400, "目标日期不能早于今天");
        }
        // 教室存在且启用（无物理外键，历史/停用教室需显式拦截）
        validateClassroomExists(lesson.getClassroomId());

        // 以原课次身份构造目标载荷做冲突预检（checkConflict 会忽略同 id 的自冲突）
        ScheduleLesson probe = new ScheduleLesson();
        probe.setId(lesson.getId());
        probe.setClassId(lesson.getClassId());
        probe.setTeacherId(lesson.getTeacherId());
        probe.setClassroomId(lesson.getClassroomId());
        probe.setLessonDate(request.getLessonDate());
        probe.setStartTime(request.getStartTime());
        probe.setEndTime(request.getEndTime());
        List<String> conflicts = scheduleConflictService.checkConflict(probe);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "快速调课冲突：" + String.join("；", conflicts));
        }

        // CAS 原子更新（防并发覆盖）：
        // 快速调课是"直接改时间、status 保持 1"，仅按 status=1 更新无法阻止同状态并发编辑——
        // T1 先提交后 status 仍为 1，T2 的 CAS 若只校验 status 会静默覆盖 T1。
        // 因此 WHERE 追加方法开头读取的"旧时间"条件：并发第二笔读到的仍是旧快照，
        // 更新时旧时间已不匹配 → updated=0 → 409，避免丢失先提交教务的改动与其通知。
        int updated = scheduleLessonMapper.update(null, new LambdaUpdateWrapper<ScheduleLesson>()
                .eq(ScheduleLesson::getId, id)
                .eq(ScheduleLesson::getStatus, 1)
                .eq(ScheduleLesson::getLessonDate, lesson.getLessonDate())
                .eq(ScheduleLesson::getStartTime, lesson.getStartTime())
                .eq(ScheduleLesson::getEndTime, lesson.getEndTime())
                .set(ScheduleLesson::getLessonDate, request.getLessonDate())
                .set(ScheduleLesson::getStartTime, request.getStartTime())
                .set(ScheduleLesson::getEndTime, request.getEndTime()));
        if (updated == 0) {
            throw new BusinessException(409, "课次已被其他教务调整，请刷新后重试");
        }

        // 旧时间信息用于文案/审计
        LocalDate oldDate = lesson.getLessonDate();
        LocalTime oldStart = lesson.getStartTime();
        LocalTime oldEnd = lesson.getEndTime();

        ScheduleLesson updatedLesson = scheduleLessonMapper.selectById(id);
        populateScheduleNames(List.of(updatedLesson));

        // 通知影响范围（在事务内解析，afterCommit 派发，避免幽灵通知）
        Set<Long> parentIds = resolveParentIds(lesson.getClassId());
        String reason = request.getReason() == null || request.getReason().isBlank()
                ? "教务快速调课" : request.getReason().trim();

        final String changeDesc = oldDate + " " + oldStart + "-" + oldEnd
                + " 调整为 " + request.getLessonDate() + " " + request.getStartTime() + "-" + request.getEndTime();
        final Long relatedId = id;

        runAfterCommit(() -> {
            try {
                if (lesson.getTeacherId() != null) {
                    Notification teacherNotification = new Notification();
                    teacherNotification.setUserId(lesson.getTeacherId());
                    teacherNotification.setType("SCHEDULE_CHANGE");
                    teacherNotification.setTitle("课次时间已调整");
                    teacherNotification.setContent(changeDesc + "（原因：" + reason + "）");
                    teacherNotification.setRelatedId(relatedId);
                    notificationService.send(lesson.getTeacherId(), teacherNotification);
                }
                if (!parentIds.isEmpty()) {
                    Notification parentNotification = new Notification();
                    parentNotification.setType("SCHEDULE_CHANGE");
                    parentNotification.setTitle("课程时间调整通知");
                    parentNotification.setContent("您孩子所在班级的课程时间已调整：" + changeDesc);
                    parentNotification.setRelatedId(relatedId);
                    notificationService.sendToUsers(new ArrayList<>(parentIds), parentNotification);
                }
            } catch (Exception e) {
                log.warn("快速调课通知发送失败, lessonId={}", id, e);
            }
        });

        // 操作日志（失败不影响主流程）
        try {
            operationLogService.log("排课管理", "快速调课（课次id=" + id + "，"
                    + oldDate + " " + oldStart + "-" + oldEnd + " → "
                    + request.getLessonDate() + " " + request.getStartTime() + "-" + request.getEndTime()
                    + "，原因：" + reason + "）");
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return updatedLesson;
    }

    @Override
    public Map<String, Object> getNotifyScope(Long id) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在或已删除");
        }
        Long teacherId = lesson.getTeacherId();
        String teacherName = "";
        if (teacherId != null) {
            User teacher = userMapper.selectById(teacherId);
            if (teacher != null) {
                teacherName = teacher.getRealName() != null && !teacher.getRealName().isBlank()
                        ? teacher.getRealName()
                        : (teacher.getUsername() != null ? teacher.getUsername() : "");
            }
        }
        int parentCount = resolveParentIds(lesson.getClassId()).size();
        return Map.of(
                "teacherId", teacherId == null ? 0L : teacherId,
                "teacherName", teacherName,
                "parentCount", parentCount);
    }

    /** 解析某班级在班学员的去重家长 userId 集合 */
    private Set<Long> resolveParentIds(Long classId) {
        if (classId == null || classStudentMapper == null || parentStudentMapper == null) {
            return java.util.Collections.emptySet();
        }
        List<ClassStudent> classStudents = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .eq(ClassStudent::getClassId, classId)
                        .eq(ClassStudent::getStatus, 1));
        if (classStudents.isEmpty()) {
            return java.util.Collections.emptySet();
        }
        Set<Long> studentIds = classStudents.stream()
                .map(ClassStudent::getStudentId).collect(Collectors.toSet());
        return parentStudentMapper.selectList(
                        new LambdaQueryWrapper<ParentStudent>().in(ParentStudent::getStudentId, studentIds))
                .stream().map(ParentStudent::getParentUserId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
    }

    /** 有事务时 afterCommit 派发，无事务直接派发（通知失败不影响主业务） */
    private void runAfterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    try {
                        action.run();
                    } catch (Exception ignored) {
                        // 通知失败不影响主业务
                    }
                }
            });
        } else {
            try {
                action.run();
            } catch (Exception ignored) {
                // 通知失败不影响主业务
            }
        }
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