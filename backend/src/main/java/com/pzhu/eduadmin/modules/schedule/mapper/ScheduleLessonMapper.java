package com.pzhu.eduadmin.modules.schedule.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.schedule.entity.ScheduleLesson;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ScheduleLessonMapper extends BaseMapper<ScheduleLesson> {

    @Select("<script>SELECT * FROM schedule_lesson WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<ScheduleLesson> selectByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}
