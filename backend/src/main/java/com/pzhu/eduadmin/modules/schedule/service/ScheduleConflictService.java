package com.pzhu.eduadmin.modules.schedule.service;

import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;

import java.util.List;

public interface ScheduleConflictService {

    /** 检测排课冲突，返回冲突描述列表。空列表表示无冲突。 */
    List<String> checkConflict(ScheduleLesson lesson);
}
