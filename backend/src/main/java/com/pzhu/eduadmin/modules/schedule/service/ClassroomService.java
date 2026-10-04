package com.pzhu.eduadmin.modules.schedule.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.schedule.entity.Classroom;
import com.pzhu.eduadmin.modules.schedule.entity.RoomBooking;

public interface ClassroomService {

    Page<Classroom> pageClassrooms(int pageNum, int pageSize, String keyword, String sortField, String sortOrder);

    Classroom getClassroomById(Long id);

    Classroom createClassroom(Classroom classroom);

    Classroom updateClassroom(Classroom classroom);

    boolean deleteClassroom(Long id);

    Page<RoomBooking> pageRoomBookings(int pageNum, int pageSize);

    RoomBooking createRoomBooking(RoomBooking booking);

    /** 校验课次引用的教室存在（null 放行；selectById 遵循 @TableLogic）；且须为启用状态 */
    void validateClassroomExists(Long classroomId);
}