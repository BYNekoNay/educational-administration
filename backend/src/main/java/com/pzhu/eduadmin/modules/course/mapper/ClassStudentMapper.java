package com.pzhu.eduadmin.modules.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface ClassStudentMapper extends BaseMapper<ClassStudent> {

    /**
     * 批量统计班级在班人数（status=1）。
     * 返回列表中每行包含 classId、cnt 两个字段。
     * 未在班列表中的班级 count=0（不会出现在结果里）。
     */
    @Select("<script>SELECT class_id AS classId, COUNT(*) AS cnt FROM class_student "
            + "WHERE class_id IN "
            + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach> "
            + "AND status = 1 AND is_deleted = 0 "
            + "GROUP BY class_id</script>")
    List<Map<String, Object>> countActiveStudentsByClassIds(@Param("ids") Collection<Long> ids);
}
