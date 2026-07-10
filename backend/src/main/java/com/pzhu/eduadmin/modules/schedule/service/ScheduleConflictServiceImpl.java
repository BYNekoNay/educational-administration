package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.mapper.ClassStudentMapper;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.mapper.RoomBookingMapper;
import com.pzhu.eduadmin.modules.schedule.mapper.ScheduleLessonMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ScheduleConflictServiceImpl implements ScheduleConflictService {

    private final ScheduleLessonMapper scheduleLessonMapper;
    private final RoomBookingMapper roomBookingMapper;
    private final ClassStudentMapper classStudentMapper;

    @Override
    public List<String> checkConflict(ScheduleLesson lesson) {
        List<String> conflicts = new ArrayList<>();

        // 查询同一天 status IN (1,2) 的所有课次
        List<ScheduleLesson> existingLessons = scheduleLessonMapper.selectList(
                new LambdaQueryWrapper<ScheduleLesson>()
                        .eq(ScheduleLesson::getLessonDate, lesson.getLessonDate())
                        .in(ScheduleLesson::getStatus, 1, 2));

        LocalTime newStart = lesson.getStartTime();
        LocalTime newEnd = lesson.getEndTime();
        LocalDateTime newStartDateTime = LocalDateTime.of(lesson.getLessonDate(), newStart);
        LocalDateTime newEndDateTime = LocalDateTime.of(lesson.getLessonDate(), newEnd);

        // 1-3. 教师/教室/班级冲突检测
        for (ScheduleLesson existing : existingLessons) {
            if (existing.getId() != null && existing.getId().equals(lesson.getId())) continue;

            LocalTime existStart = existing.getStartTime();
            LocalTime existEnd = existing.getEndTime();
            boolean overlap = existStart.isBefore(newEnd) && existEnd.isAfter(newStart);
            if (!overlap) continue;

            if (lesson.getTeacherId() != null && lesson.getTeacherId().equals(existing.getTeacherId())) {
                conflicts.add(String.format("教师冲突：该教师 %s %s-%s 已有课次(id=%d)",
                        existing.getLessonDate(), existing.getStartTime(), existing.getEndTime(), existing.getId()));
            }
            if (lesson.getClassroomId() != null && lesson.getClassroomId().equals(existing.getClassroomId())) {
                conflicts.add(String.format("教室冲突：教室(id=%d) %s %s-%s 已被课次(id=%d)占用",
                        existing.getClassroomId(), existing.getLessonDate(), existing.getStartTime(), existing.getEndTime(), existing.getId()));
            }
            if (lesson.getClassId() != null && lesson.getClassId().equals(existing.getClassId())) {
                conflicts.add(String.format("班级冲突：班级(id=%d) %s %s-%s 已有课次(id=%d)",
                        existing.getClassId(), existing.getLessonDate(), existing.getStartTime(), existing.getEndTime(), existing.getId()));
            }
        }

        // 4. 教室预约冲突检测（room_booking）
        if (lesson.getClassroomId() != null) {
            List<RoomBooking> roomBookings = roomBookingMapper.selectList(
                    new LambdaQueryWrapper<RoomBooking>()
                            .eq(RoomBooking::getClassroomId, lesson.getClassroomId()));
            for (RoomBooking booking : roomBookings) {
                boolean overlap = newStartDateTime.isBefore(booking.getEndTime())
                        && newEndDateTime.isAfter(booking.getStartTime());
                if (overlap) {
                    conflicts.add(String.format("教室预约冲突：教室(id=%d) %s 已被预约(id=%d，用途=%s)",
                            booking.getClassroomId(), booking.getStartTime(), booking.getId(), booking.getPurpose()));
                }
            }
        }

        // 5. 学生跨课次冲突检测
        if (lesson.getClassId() != null) {
            detectStudentConflicts(lesson, existingLessons, newStart, newEnd, conflicts);
        }

        return conflicts;
    }

    /**
     * 检测同一班级的学员是否在其他班级的课次中存在时间冲突
     */
    private void detectStudentConflicts(ScheduleLesson lesson, List<ScheduleLesson> existingLessons,
                                         LocalTime newStart, LocalTime newEnd, List<String> conflicts) {
        // 获取本班级学员列表
        List<ClassStudent> classStudents = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>().eq(ClassStudent::getClassId, lesson.getClassId()));
        if (classStudents.isEmpty()) return;

        Set<Long> studentIds = classStudents.stream()
                .map(ClassStudent::getStudentId).collect(Collectors.toSet());

        // 获取这些学员在其他班级的报名记录
        List<ClassStudent> otherEnrollments = classStudentMapper.selectList(
                new LambdaQueryWrapper<ClassStudent>()
                        .in(ClassStudent::getStudentId, studentIds)
                        .ne(ClassStudent::getClassId, lesson.getClassId()));
        if (otherEnrollments.isEmpty()) return;

        Set<Long> otherClassIds = otherEnrollments.stream()
                .map(ClassStudent::getClassId).collect(Collectors.toSet());

        // 检查同一天这些班级是否有课次，且时间交叉
        for (ScheduleLesson existing : existingLessons) {
            if (existing.getClassId() == null) continue;
            if (!otherClassIds.contains(existing.getClassId())) continue;

            LocalTime existStart = existing.getStartTime();
            LocalTime existEnd = existing.getEndTime();
            boolean overlap = existStart.isBefore(newEnd) && existEnd.isAfter(newStart);
            if (!overlap) continue;

            // 找出冲突的学员
            Set<Long> conflictStudentIds = otherEnrollments.stream()
                    .filter(e -> e.getClassId().equals(existing.getClassId()))
                    .map(ClassStudent::getStudentId)
                    .filter(studentIds::contains)
                    .collect(Collectors.toSet());

            if (!conflictStudentIds.isEmpty()) {
                conflicts.add(String.format("学员冲突：学员(id=%s)在班级(id=%d) %s %s-%s 已有课次(id=%d)",
                        conflictStudentIds, existing.getClassId(),
                        existing.getLessonDate(), existing.getStartTime(), existing.getEndTime(), existing.getId()));
            }
        }
    }
}
