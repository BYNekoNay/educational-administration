package com.pzhu.eduadmin.modules.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.Course;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {

    /**
     * 绕过 @TableLogic 批量查询课程名（含已软删课程）。
     * 用于历史收费/课时账户/报名等需要保留课程名的场景。
     */
    @Select("<script>SELECT id, name FROM course WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectNamesByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);

    /**
     * 绕过 @TableLogic 按课程名模糊匹配课程 ID（含已逻辑删除的课程）。
     * 与 {@link #selectNamesByIdsIncludeDeleted} 保持同口径，避免财务列表出现
     * "显示得出来、搜索搜不到"的不一致。名称经 #{name} 参数化，无注入风险。
     */
    @Select("SELECT id, name FROM course WHERE name LIKE CONCAT('%', #{name}, '%')")
    List<Map<String, Object>> selectIdsByNameLikeIncludeDeleted(@Param("name") String name);

    /**
     * 绕过 @TableLogic 按 ID 查询完整课程对象（含已软删课程）。
     * 用于退费计算等需要课程价格/总课时等字段的业务场景。
     */
    @Select("SELECT * FROM course WHERE id = #{id}")
    Course selectByIdIncludeDeleted(@Param("id") Long id);

    /**
     * 查询某课程下所有开班（status=1，按开班日期升序）
     * 用于家长在线报名选班展示
     */
    @Select("SELECT * FROM class_group WHERE course_id = #{courseId} AND status = 1 AND is_deleted = 0 ORDER BY start_date ASC, id ASC")
    List<ClassGroup> selectClassGroupsByCourseId(@Param("courseId") Long courseId);
}
