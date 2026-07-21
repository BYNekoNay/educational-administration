package com.pzhu.eduadmin.modules.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ClassGroupMapper extends BaseMapper<ClassGroup> {

    /**
     * 绕过 @TableLogic 批量查询班级名（含已软删班级）。
     * 用于历史报名/考勤等需要保留班级名的场景。
     */
    @Select("<script>SELECT id, class_name FROM class_group WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectClassNamesByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 批量查询"班级 → 课程名"（绕过 @TableLogic，包含已软删课程名）
     * 用于课表等需要展示"课程名"但只有班级 ID 的场景
     */
    @Select("<script>SELECT cg.id AS id, c.name AS name FROM class_group cg JOIN course c ON c.id = cg.course_id WHERE cg.id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectCourseNamesByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);

    /**
     * M1 fix: 绕过 @TableLogic 查询班级的 courseId（含已软删班级）
     */
    @Select("SELECT course_id FROM class_group WHERE id = #{classId}")
    Long selectCourseIdByIdIncludeDeleted(@Param("classId") Long classId);
}
