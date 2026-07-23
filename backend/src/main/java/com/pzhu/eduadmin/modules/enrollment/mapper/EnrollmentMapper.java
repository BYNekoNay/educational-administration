package com.pzhu.eduadmin.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {

    /**
     * 查询报名对应的课程ID。
     * 返回类型为 Long（非实体），MyBatis-Plus 的逻辑删除拦截器不会对标量查询追加 is_deleted=0，
     * 因此即使报名已被逻辑删除，仍可取到其 course_id（退费审核需要据此定位课时账户）。
     */
    @Select("SELECT course_id FROM enrollment WHERE id = #{id}")
    Long selectCourseIdById(@Param("id") Long id);

    /**
     * 按 ID 批量查询报名记录，绕过逻辑删除过滤（is_deleted）。
     * 用于利润统计：已退班/已删除的报名仍需参与退费→课程映射，否则会高估课程利润。
     */
    @Select("<script>SELECT * FROM enrollment WHERE id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Enrollment> selectBatchIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}
