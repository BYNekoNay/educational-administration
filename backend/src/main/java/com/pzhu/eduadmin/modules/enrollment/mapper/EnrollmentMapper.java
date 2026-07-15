package com.pzhu.eduadmin.modules.enrollment.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {

    /**
     * 查询报名对应的课程ID。
     * 返回类型为 Long（非实体），MyBatis-Plus 的逻辑删除拦截器不会对标量查询追加 is_deleted=0，
     * 因此即使报名已被逻辑删除，仍可取到其 course_id（退费审核需要据此定位课时账户）。
     */
    @Select("SELECT course_id FROM enrollment WHERE id = #{id}")
    Long selectCourseIdById(@Param("id") Long id);
}
