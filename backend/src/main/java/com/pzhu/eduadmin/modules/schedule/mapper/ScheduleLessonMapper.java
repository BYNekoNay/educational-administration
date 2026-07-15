package com.pzhu.eduadmin.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ScheduleLessonMapper extends BaseMapper<ScheduleLesson> {

    @Select("SELECT * FROM schedule_lesson WHERE id IN (${ids})")
    List<ScheduleLesson> selectByIdsIncludeDeleted(@Param("ids") String ids);
}
