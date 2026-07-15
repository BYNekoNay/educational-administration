package com.pzhu.eduadmin.modules.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface ClassGroupMapper extends BaseMapper<ClassGroup> {

    /**
     * 绕过 @TableLogic 批量查询班级名（含已软删班级）。
     * 用于历史报名/考勤等需要保留班级名的场景。
     */
    @Select("SELECT id, class_name FROM class_group WHERE id IN (${ids})")
    List<Map<String, Object>> selectClassNamesByIdsIncludeDeleted(@Param("ids") String ids);
}
