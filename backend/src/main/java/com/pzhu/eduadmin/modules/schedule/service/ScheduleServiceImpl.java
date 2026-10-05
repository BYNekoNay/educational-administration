package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.CacheNames;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.attendance.entity.Attendance;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDate;
import java.math.BigDecimal;
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

    // 教室/预约 与 调课申请 的职责已拆出到独立服务（均注册为 Spring Bean，此处注入）；
    // 纯单元测试（Mockito @InjectMocks、无 Spring 上下文）下字段为 null，
    // 访问器回退到用本类已注入的 mapper 手工装配，保证既有用例零改动可运行。
    @Autowired
    private ClassroomService classroomService;
    @Autowired
    private AdjustRequestService adjustRequestService;

    private ClassroomService classroomService() {
        if (classroomService == null) {
            classroomService = new ClassroomServiceImpl(
                    classroomMapper, roomBookingMapper, scheduleLessonMapper, operationLogService);
        }
        return classroomService;
    }

    private AdjustRequestService adjustRequestService() {
        if (adjustRequestService == null) {
            adjustRequestService = new AdjustRequestServiceImpl(
                    scheduleAdjustRequestMapper, scheduleLessonMapper, classGroupMapper, courseMapper,
                    periodMapper, userMapper, classroomMapper, attendanceService, operationLogService,
                    scheduleConflictService, classroomService());
        }
        return adjustRequestService;
    }

    private static final Map<String, SFunction<ScheduleLesson, ?>> LESSON_SORT_MAP = Map.of(
            "id", ScheduleLesson::getId,
            "lessonDate", ScheduleLesson::getLessonDate,
            "startTime", ScheduleLesson::getStartTime,
            "status", ScheduleLesson::getStatus
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

        // 关键字过滤下推到数据库查询，避免分页后内存过滤导致结果不正确
        if (keyword != null && !keyword.isBlank()) {
            // 转义 LIKE 元字符（与 QueryHelper.applyKeyword 一致），防止用户输入的 %/_ 被当作通配符
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
    @CacheEvict(cacheNames = {CacheNames.DASHBOARD, CacheNames.RISK_WARNINGS, CacheNames.RISK_SUMMARY}, allEntries = true)
    public ScheduleLesson createLesson(ScheduleLesson lesson) {
        // 关键字段为空时冲突检测会被跳过，导致插入无效课次
        if (lesson.getLessonDate() == null || lesson.getStartTime() == null || lesson.getEndTime() == null) {
            throw new BusinessException(400, "课次日期和起止时间不能为空");
        }
        // 校验起止时间合法性
        if (!lesson.getEndTime().isAfter(lesson.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        // 校验引用的教室存在（无外键约束，冲突检测对未知教室恒通过，会留下悬空引用）
        classroomService().validateClassroomExists(lesson.getClassroomId());
        List<String> conflicts = scheduleConflictService.checkConflict(lesson);
        if (!conflicts.isEmpty()) {
            throw new BusinessException(409, "排课冲突：" + String.join("；", conflicts));
        }
        scheduleLessonMapper.insert(lesson);
        notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "新增课次安排");
        return lesson;
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
        // send() 是 @Async，会立即在独立线程入库并推送 SSE。若在事务提交前派发，
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
        // 加载现有记录并合并非 null 字段，确保部分更新时冲突检测使用完整数据
        ScheduleLesson existing = scheduleLessonMapper.selectById(lesson.getId());
        if (existing == null) throw new BusinessException(404, "课次不存在或已删除");
        // 仅允许编辑待上课（status=1）课次。已完成（status=2）课次可能已产生考勤扣减，
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
        // 不接受客户端覆盖 status。状态变更必须走调课/取消专用流程（含考勤冲销），
        // 直接置 status=4/2 会绕过冲销导致课时账户不一致，非法值还会污染 status IN (1,2) 过滤。

        // 变更教室时校验新教室存在（仅当请求携带 classroomId，避免误伤引用已删除教室的历史课次）
        if (lesson.getClassroomId() != null) {
            classroomService().validateClassroomExists(lesson.getClassroomId());
        }

        // 校验合并后的起止时间合法性
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
    @CacheEvict(cacheNames = {CacheNames.DASHBOARD, CacheNames.RISK_WARNINGS, CacheNames.RISK_SUMMARY}, allEntries = true)
    public boolean deleteLesson(Long id) {
        ScheduleLesson lesson = scheduleLessonMapper.selectById(id);
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        // 仅允许删除待上课（status=1）的课次。已完成（status=2）等状态的课次
        // 可能已产生考勤扣减，直接删除会导致课时账户永久不一致（扣减无法回滚）。
        if (lesson.getStatus() != null && lesson.getStatus() != 1) {
            throw new BusinessException(409, "该课次已非待上课状态，无法删除（如需调整请使用调课/取消功能）");
        }
        // 【P0 修复】上面的状态守卫不足以保证没有课时扣减。
        // 原实现隐含假设"status=1（待上课）的课次尚无考勤"，但考勤允许对待上课课次提交
        // 并扣减课时（见 AttendanceServiceImpl），因此先考勤再删课会让已扣课时永久丢失：
        // 课次记录消失后 reverseDeductByLessonId 无从回冲，学员课时账户凭空少账且无流水。
        // 这里显式检查是否存在已扣课时的考勤，有则拒绝删除并引导走调课/取消流程。
        List<Attendance> lessonAttendances = attendanceService.getByLessonId(id);
        if (lessonAttendances != null) {
            boolean hasDeduction = lessonAttendances.stream()
                    .anyMatch(a -> a.getDeductLessons() != null
                            && a.getDeductLessons().compareTo(BigDecimal.ZERO) > 0);
            if (hasDeduction) {
                throw new BusinessException(409,
                        "该课次已产生课时扣减，删除将导致课时无法回冲。请使用调课或取消功能");
            }
        }
        boolean deleted = scheduleLessonMapper.deleteById(id) > 0;
        if (deleted) {
            notifyLessonTeacher(lesson, "SCHEDULE_CHANGE", "课次已取消");
        }
        // 日志失败不应影响删除结果（删除已提交）
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
    @CacheEvict(cacheNames = {CacheNames.DASHBOARD, CacheNames.RISK_WARNINGS, CacheNames.RISK_SUMMARY}, allEntries = true)
    public void batchCreate(List<ScheduleLesson> lessons) {
        // 批量创建同样需要逐条校验，避免空时间或非法时间段绕过冲突检测被插入
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
    @CacheEvict(cacheNames = {CacheNames.DASHBOARD, CacheNames.RISK_WARNINGS, CacheNames.RISK_SUMMARY}, allEntries = true)
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
        // 教师单日课次上限：先统计候选区间内该教师已有课次（待上课/已完成）按日期的负载
        Map<LocalDate, Long> teacherDailyLoad = new HashMap<>();
        if (request.getMaxLessonsPerDay() != null) {
            scheduleLessonMapper.selectList(new LambdaQueryWrapper<ScheduleLesson>()
                            .eq(ScheduleLesson::getTeacherId, request.getTeacherId())
                            .between(ScheduleLesson::getLessonDate, request.getStartDate(), request.getEndDate())
                            .in(ScheduleLesson::getStatus, List.of(1, 2)))
                    .forEach(lesson -> teacherDailyLoad.merge(lesson.getLessonDate(), 1L, Long::sum));
        }
        List<ScheduleLesson> generated = new ArrayList<>();
        LocalDate date = request.getStartDate();
        while (!date.isAfter(request.getEndDate()) && generated.size() < request.getLessonCount()) {
            if (weekdays.contains(date.getDayOfWeek().getValue())) {
                // 候选日该教师课次已达上限：跳过该日期，把课次顺延到后续可用日期
                if (request.getMaxLessonsPerDay() != null
                        && teacherDailyLoad.getOrDefault(date, 0L) >= request.getMaxLessonsPerDay()) {
                    date = date.plusDays(1);
                    continue;
                }
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
        return classroomService().pageClassrooms(pageNum, pageSize, keyword, sortField, sortOrder);
    }

    @Override
    public Classroom getClassroomById(Long id) {
        return classroomService().getClassroomById(id);
    }

    @Override
    public Classroom createClassroom(Classroom classroom) {
        return classroomService().createClassroom(classroom);
    }

    @Override
    public Classroom updateClassroom(Classroom classroom) {
        return classroomService().updateClassroom(classroom);
    }

    @Override
    public boolean deleteClassroom(Long id) {
        return classroomService().deleteClassroom(id);
    }

    @Override
    public Page<RoomBooking> pageRoomBookings(int pageNum, int pageSize) {
        return classroomService().pageRoomBookings(pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RoomBooking createRoomBooking(RoomBooking booking) {
        return classroomService().createRoomBooking(booking);
    }

    @Override
    public Page<AdjustRequestVO> pageAdjustRequests(int pageNum, int pageSize, Integer status) {
        return adjustRequestService().pageAdjustRequests(pageNum, pageSize, status);
    }

    @Override
    public Page<AdjustRequestVO> pageTeacherAdjustRequests(Long userId, int pageNum, int pageSize) {
        return adjustRequestService().pageTeacherAdjustRequests(userId, pageNum, pageSize);
    }

    @Override
    public ScheduleAdjustRequest createAdjustRequest(ScheduleAdjustRequest request) {
        return adjustRequestService().createAdjustRequest(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleAdjustRequest auditAdjustRequest(Long id, Integer status, Long auditorId, String remark) {
        return adjustRequestService().auditAdjustRequest(id, status, auditorId, remark);
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
        classroomService().validateClassroomExists(lesson.getClassroomId());

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