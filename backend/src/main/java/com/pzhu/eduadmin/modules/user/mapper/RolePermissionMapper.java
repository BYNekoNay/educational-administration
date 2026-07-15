package com.pzhu.eduadmin.modules.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RolePermissionMapper extends BaseMapper<RolePermission> {

    /**
     * 物理删除角色的全部权限关联（绕过 @TableLogic 逻辑删除）。
     *
     * <p>使用场景：保存角色权限时，需要"先清后建"。若改用 MP 默认的逻辑删除，
     * 旧行（is_deleted=1）会持续占用 (role_code, permission_code) 唯一键，
     * 再次插入同 (role_code, permission_code) 行时立刻触发 409 DuplicateKey。</p>
     */
    @Delete("DELETE FROM role_permission WHERE role_code = #{roleCode}")
    int realDeleteByRoleCode(@Param("roleCode") String roleCode);
}
