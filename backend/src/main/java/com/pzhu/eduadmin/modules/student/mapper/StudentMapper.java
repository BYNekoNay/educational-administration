package com.pzhu.eduadmin.modules.student.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.student.entity.Student;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface StudentMapper extends BaseMapper<Student> {

    /**
     * 绕过 @TableLogic 批量查询学员姓名（含已逻辑删除的学员）。
     *
     * <p>用途：历史流水/历史退费/历史考勤等场景需要按 student_id 关联出当时姓名，
     * 学员可能被退课/退费后软删（is_deleted=1），若走 selectBatchIds 会被拦截器过滤掉，
     * 导致历史记录显示空姓名。这里用 @Select + Map<String,Object> 返回类型，让
     * LogicDeleteInnerInterceptor 解析不到 TableInfo，不会追加 is_deleted=0 条件。</p>
     *
     * <p>返回结果：[{id:1, name:"刘小小"}, {id:2, name:"陈朵朵"}, ...]（含已软删学员）</p>
     */
    @Select("<script>SELECT id, name FROM student WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectNamesByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}
