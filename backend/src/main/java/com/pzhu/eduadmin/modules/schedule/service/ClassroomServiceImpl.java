package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.ClassroomMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.RoomBookingMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 教室与教室预约服务：从 {@link ScheduleServiceImpl} 按职责拆分而来，逻辑与原实现保持一致。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomMapper classroomMapper;
    private final RoomBookingMapper roomBookingMapper;
    private final ScheduleLessonMapper scheduleLessonMapper;
    private final OperationLogService operationLogService;

    private static final Map<String, SFunction<Classroom, ?>> ROOM_SORT_MAP = Map.of(
            "id", Classroom::getId,
            "name", Classroom::getName,
            "capacity", Classroom::getCapacity
    );

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
        // 校验存在性 + 名称/容量合法性（与 createClassroom 对齐），仅校验请求携带的字段
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
        // 检查该教室是否有未来排课，防止删除后产生孤立记录
        long futureLessons = scheduleLessonMapper.selectCount(new LambdaQueryWrapper<ScheduleLesson>()
                .eq(ScheduleLesson::getClassroomId, id)
                .ge(ScheduleLesson::getLessonDate, LocalDate.now())
                .in(ScheduleLesson::getStatus, 1, 2));
        if (futureLessons > 0) {
            throw new BusinessException(409, "该教室有未来排课，无法删除");
        }
        // 未来预约也需拦截，否则删除教室后孤立预约仍按 classroomId 阻塞排课冲突检测
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
        // 输入校验
        if (booking.getClassroomId() == null) throw new BusinessException(400, "教室ID不能为空");
        if (booking.getStartTime() == null || booking.getEndTime() == null) {
            throw new BusinessException(400, "预约开始/结束时间不能为空");
        }
        if (!booking.getEndTime().isAfter(booking.getStartTime())) {
            throw new BusinessException(400, "结束时间必须晚于开始时间");
        }
        // 校验教室存在（selectById 遵循 @TableLogic，已删除教室视为不存在），防止预约引用孤立教室
        Classroom classroom = classroomMapper.selectById(booking.getClassroomId());
        if (classroom == null) {
            throw new BusinessException(404, "教室不存在");
        }
        // 停用教室（status!=1）不可预约（autoSchedule 仅用 status=1 教室，此处需对齐）
        if (!Integer.valueOf(1).equals(classroom.getStatus())) {
            throw new BusinessException(409, "该教室已停用，无法预约");
        }
        // 拒绝已结束的历史预约，避免污染冲突检测
        if (booking.getEndTime().isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "预约结束时间不能早于当前时间");
        }
        // 检查同一教室是否存在时间段重叠的预约
        long conflicts = roomBookingMapper.selectCount(new LambdaQueryWrapper<RoomBooking>()
                .eq(RoomBooking::getClassroomId, booking.getClassroomId())
                .lt(RoomBooking::getStartTime, booking.getEndTime())
                .gt(RoomBooking::getEndTime, booking.getStartTime()));
        if (conflicts > 0) {
            throw new BusinessException(409, "该时段教室已被预约");
        }
        // 预约还需对称地检查已排课次——排课侧会查预约冲突，但预约侧此前只查预约，
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
        // 插入后二次校验（防止并发 TOCTOU），若冲突则回滚
        long postConflicts = roomBookingMapper.selectCount(new LambdaQueryWrapper<RoomBooking>()
                .eq(RoomBooking::getClassroomId, booking.getClassroomId())
                .lt(RoomBooking::getStartTime, booking.getEndTime())
                .gt(RoomBooking::getEndTime, booking.getStartTime()));
        if (postConflicts > 1) {
            throw new BusinessException(409, "该时段教室已被预约（并发冲突）");
        }
        return booking;
    }

    /** 校验课次引用的教室存在（null 放行；selectById 遵循 @TableLogic）；且须为启用状态 */
    @Override
    public void validateClassroomExists(Long classroomId) {
        if (classroomId == null) return;
        Classroom classroom = classroomMapper.selectById(classroomId);
        if (classroom == null) {
            throw new BusinessException(404, "教室不存在(id=" + classroomId + ")");
        }
        // 停用教室(status!=1)不可排课，与 autoSchedule/createRoomBooking 的 status=1 不变式对齐
        if (!Integer.valueOf(1).equals(classroom.getStatus())) {
            throw new BusinessException(409, "该教室已停用，无法排课(id=" + classroomId + ")");
        }
    }
}