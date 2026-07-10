package com.pzhu.eduadmin.modules.user.service;

import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.Role;

import java.util.List;

public interface RoleService {

    List<Role> listRoles();

    Role getRoleById(Long id);

    Role createRole(String roleCode, String roleName);

    void deleteRole(Long id);

    List<String> getRolePermissions(String roleCode);

    void updateRolePermissions(String roleCode, List<String> permissionCodes);
}
