package com.pzhu.eduadmin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.user.entity.Role;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RoleMapper extends BaseMapper<Role> {

    /** 物理删除角色，绕过 @TableLogic 避免 role_code 唯一键冲突 */
    @Delete("DELETE FROM role WHERE role_code = #{roleCode}")
    int realDeleteByRoleCode(@Param("roleCode") String roleCode);
}
