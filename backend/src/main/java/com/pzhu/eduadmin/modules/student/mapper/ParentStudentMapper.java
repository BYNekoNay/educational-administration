package com.pzhu.eduadmin.modules.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ParentStudentMapper extends BaseMapper<ParentStudent> {

    /**
     * 物理删除家长学员绑定关系。
     * 由于 parent_student 表存在唯一键 uk_parent_student(parent_user_id, student_id)，
     * 若用 MyBatis-Plus 的逻辑删除（is_deleted=1）解绑，唯一键仍生效，会导致后续无法再次绑定同一组合。
     * 因此解绑采用物理删除，彻底移除该行。
     */
    @Delete("DELETE FROM parent_student WHERE student_id = #{studentId} AND parent_user_id = #{parentUserId}")
    int physicalDelete(@Param("studentId") Long studentId, @Param("parentUserId") Long parentUserId);

    /**
     * 查询指定家长-学员组合的记录数（含已逻辑删除的行），用于绑定前判重。
     * 返回标量，MyBatis-Plus 逻辑删除拦截器对标量返回不追加 is_deleted=0 条件，因此能统计到历史软删行。
     */
    @Select("SELECT COUNT(*) FROM parent_student WHERE student_id = #{studentId} AND parent_user_id = #{parentUserId}")
    int countIncludingDeleted(@Param("studentId") Long studentId, @Param("parentUserId") Long parentUserId);
}
