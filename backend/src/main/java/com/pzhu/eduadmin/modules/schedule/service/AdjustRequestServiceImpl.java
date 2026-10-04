package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.attendance.service.AttendanceService;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.schedule.dto.AdjustRequestVO;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.Period;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ClassroomMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.PeriodMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleAdjustRequestMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 调课申请服务：从 {@link ScheduleServiceImpl} 按职责拆分而来，逻辑与原实现保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdjustRequestServiceImpl implements AdjustRequestService {

    private final ScheduleAdjustRequestMapper scheduleAdjustRequestMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final ClassGroupMapper classGroupMapper;
    private final CourseMapper courseMapper;
    private final PeriodMapper periodMapper;
    private final UserMapper userMapper;
    private final ClassroomMapper classroomMapper;
    private final AttendanceService attendanceService;
    private final OperationLogService operationLogService;
    private final ScheduleConflictService scheduleConflictService;
    private final ClassroomService classroomService;

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
        // 校验课次存在
        if (request.getLessonId() == null) {
            throw new BusinessException(400, "课次ID不能为空");
        }
        ScheduleLesson lesson = scheduleLessonMapper.selectById(request.getLessonId());
        if (lesson == null) {
            throw new BusinessException(404, "课次不存在");
        }
        // 教师角色校验课次归属，防止教师对他人课次发起调课申请
        // （与 TeacherAttendanceController.createMyAdjustRequest 的归属校验保持一致）
        com.pzhu.eduadmin.security.LoginUser loginUser = com.pzhu.eduadmin.security.CurrentUserHolder.get();
        if (loginUser != null && "TEACHER".equals(loginUser.getRoleCode())
                && !loginUser.getUserId().equals(lesson.getTeacherId())) {
            throw new BusinessException(403, "该课次不属于您，无法发起调课申请");
        }
        // 仅待上课（status=1）课次可发起调课。已完成/已调课课次的申请永远无法通过，徒增死请求且误导用户。
        if (!Integer.valueOf(1).equals(lesson.getStatus())) {
            throw new BusinessException(409, "仅待上课课次可发起调课申请");
        }
        // 同一课次已有待审批调课申请时拒绝重复提交
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
        // 校验审核状态只接受 2（通过）或 3（驳回）
        if (status == null || (status != 2 && status != 3)) {
            throw new BusinessException(400, "审核状态只能为2（通过）或3（驳回）");
        }
        ScheduleAdjustRequest request = scheduleAdjustRequestMapper.selectById(id);
        if (request == null) throw new BusinessException(404, "调课申请不存在");
        if (request.getStatus() != null && request.getStatus() != 1) {
            throw new BusinessException(409, "该调课申请已处理");
        }
        // expectTime 校验移至审核通过分支（驳回无需期望时间），
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
            // 仅审核通过时需要期望时间（用于生成新课次）
            if (request.getExpectTime() == null) {
                throw new BusinessException(400, "调课申请缺少期望时间，无法通过");
            }
            ScheduleLesson oldLesson = scheduleLessonMapper.selectById(request.getLessonId());
            if (oldLesson == null) throw new BusinessException(404, "原课次不存在或已删除，无法完成调课");
            // 原课次必须仍为待上课（status=1）。否则同一课次的多笔待审批调课申请可被重复通过，
            // 第二次审批时原课次已是 status=4，置 4 为无操作却又插入第二个替换课次（幽灵课次，统计/考勤重复计入）。
            if (!Integer.valueOf(1).equals(oldLesson.getStatus())) {
                throw new BusinessException(409, "原课次已非待上课状态，无法调课");
            }
            // 原子地将原课次 status 1→4（CAS）。仅对 request 行加 CAS 不足以防止同一课次的多笔
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
            // 历史遗留 status=1 课次可能缺起止时间，Duration.between 会 NPE(500)，
            // 且因方法带事务会使该调课申请永久卡死。提前校验给出友好错误。
            if (oldLesson.getStartTime() == null || oldLesson.getEndTime() == null) {
                throw new BusinessException(409, "原课次缺少上课时间，无法调课");
            }
            Duration lessonDuration = Duration.between(oldLesson.getStartTime(), oldLesson.getEndTime());
            newLesson.setStartTime(request.getExpectTime().toLocalTime());
            java.time.LocalTime newEndTime = request.getExpectTime().toLocalTime().plus(lessonDuration);
            // 校验调课后不跨午夜，否则冲突检测公式失效
            if (!newEndTime.isAfter(request.getExpectTime().toLocalTime())) {
                throw new BusinessException(400, "调课后的课次不能跨越午夜（结束时间早于开始时间）");
            }
            newLesson.setEndTime(newEndTime);
            newLesson.setStatus(1);

            // sourceLessonId 语义说明 ——
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

            // 调课生成的新课次继承原教室，需复核教室仍存在（原课次创建后教室可能被软删除），
            // 否则替换课次静默引用孤立教室，且冲突检测对未知教室恒通过。
            classroomService.validateClassroomExists(newLesson.getClassroomId());

            List<String> conflicts = scheduleConflictService.checkConflict(newLesson);
            if (!conflicts.isEmpty()) {
                throw new BusinessException(409, (isSubstitute ? "代课" : "调课") + "冲突：" + String.join("；", conflicts));
            }
            scheduleLessonMapper.insert(newLesson);

            // 调课审批通过后，回冲原课次已扣减的考勤课时
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
}