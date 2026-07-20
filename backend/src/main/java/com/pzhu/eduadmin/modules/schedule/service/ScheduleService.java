package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleAdjustRequest;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import com.pzhu.eduadmin.modules.schedule.dto.AutoScheduleRequest;

import java.time.LocalDate;
import java.util.List;

public interface ScheduleService {

    // 课次
    Page<ScheduleLesson> pageScheduleLessons(int pageNum, int pageSize,
            String keyword, String sortField, String sortOrder,
            Long courseId, Long classId, Long teacherId, Long classroomId,
            Integer status, LocalDate dateFrom, LocalDate dateTo);

    ScheduleLesson getLessonById(Long id);

    ScheduleLesson createLesson(ScheduleLesson lesson);

    ScheduleLesson updateLesson(ScheduleLesson lesson);

    boolean deleteLesson(Long id);

    // 冲突检测与批量排课
    List<String> checkConflict(ScheduleLesson lesson);

    void batchCreate(List<ScheduleLesson> lessons);

    List<ScheduleLesson> autoSchedule(AutoScheduleRequest request);

    // 教室
    Page<Classroom> pageClassrooms(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    Classroom getClassroomById(Long id);

    Classroom createClassroom(Classroom classroom);

    Classroom updateClassroom(Classroom classroom);

    boolean deleteClassroom(Long id);

    // 教室预约
    Page<RoomBooking> pageRoomBookings(int pageNum, int pageSize);

    RoomBooking createRoomBooking(RoomBooking booking);

    // 调课申请
    Page<ScheduleAdjustRequest> pageAdjustRequests(int pageNum, int pageSize);

    Page<ScheduleAdjustRequest> pageTeacherAdjustRequests(Long teacherId, int pageNum, int pageSize);

    ScheduleAdjustRequest createAdjustRequest(ScheduleAdjustRequest request);

    ScheduleAdjustRequest auditAdjustRequest(Long id, Integer status, Long auditorId, String remark);
}
