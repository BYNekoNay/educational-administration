package com.pzhu.eduadmin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 绕过 @TableLogic 批量查询用户姓名（含已软删用户）。
     * 用于历史班级/报名等需要保留教师姓名的场景。
     * 返回结果：[{id:1, real_name:"张三", username:"zhangsan"}, ...]
     */
    @Select("<script>SELECT id, real_name, username FROM user WHERE id IN <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>")
    List<Map<String, Object>> selectNamesByIdsIncludeDeleted(@Param("ids") Collection<Long> ids);
}
