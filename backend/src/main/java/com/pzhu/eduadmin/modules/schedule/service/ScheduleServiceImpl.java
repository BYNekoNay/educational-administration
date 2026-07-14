package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.*;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final OperationLogMapper operationLogMapper;
    private final ClassGroupMapper classGroupMapper;
    private final UserMapper userMapper;

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
    public Page<ScheduleLesson> pageScheduleLessons(int pageNum, int pageSize, String sortField, String sortOrder) {
        LambdaQueryWrapper<ScheduleLesson> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applySort(wrapper, sortField, sortOrder, LESSON_SORT_MAP, () -> wrapper.orderByDesc(ScheduleLesson::getLessonDate));
        Page<ScheduleLesson> page = scheduleLessonMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        populateScheduleNames(page.getRecords());
        return page;
    }

    /** 填充排课记录的关联名称 */
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

        for (ScheduleLesson s : list) {
            s.setClassName(classNames.getOrDefault(s.getClassId(), ""));
            s.setTeacherName(teacherNames.getOrDefault(s.getTeacherId(), ""));
            s.setClassroomName(roomNames.getOrDefault(s.getClassroomId(), ""));
        }
    }

    @Override
    public ScheduleLesson getLessonById(Long id) {
        return scheduleLessonMapper.selectById(id);
    }

    @Override
    public ScheduleLesson createLesson(ScheduleLesson lesson) {
        scheduleLessonMapper.insert(lesson);
        return lesson;
    }

    @Override
    public ScheduleLesson updateLesson(ScheduleLesson lesson) {
        scheduleLessonMapper.updateById(lesson);
        return scheduleLessonMapper.selectById(lesson.getId());
    }

    @Override
    public boolean deleteLesson(Long id) {
        // 操作日志：删除课次（对应 docs/11 §10 操作日志与审计）
        logOperation("排课管理", "删除课次(id=" + id + ")");
        return scheduleLessonMapper.deleteById(id) > 0;
    }

    @Override
    public List<String> checkConflict(ScheduleLesson lesson) {
        return scheduleConflictService.checkConflict(lesson);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void batchCreate(List<ScheduleLesson> lessons) {
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
        logOperation("教室管理", "删除教室(id=" + id + ")");
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
        request.setStatus(status);
        request.setAuditorId(auditorId);
        request.setAuditRemark(remark);
        scheduleAdjustRequestMapper.updateById(request);

        // 审核通过：落地新课次
        if (status == 2) {
            ScheduleLesson oldLesson = scheduleLessonMapper.selectById(request.getLessonId());
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
        logOperation("排课管理", status == 2 ? "审核通过调课申请(id=" + id + ")" : "驳回调课申请(id=" + id + ")");

        return request;
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
