package com.pzhu.eduadmin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.user.entity.TeacherCourse;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TeacherCourseMapper extends BaseMapper<TeacherCourse> {

    /**
     * 物理删除教师的所有特长课程关联（绕过 @TableLogic 逻辑删除）。
     * teacher_course 表有 UNIQUE KEY (user_id, course_id)，逻辑删除行会占用唯一键，
     * 导致"删旧插新"时撞键。关联表采用物理删除。
     */
    @Delete("DELETE FROM teacher_course WHERE user_id = #{userId}")
    int realDeleteByUserId(@Param("userId") Long userId);
}
