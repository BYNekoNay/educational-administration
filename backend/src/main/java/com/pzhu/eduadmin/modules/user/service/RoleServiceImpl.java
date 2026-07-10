package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.Role;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RoleMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final OperationLogMapper operationLogMapper;

    @Override
    public List<Role> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    public Role getRoleById(Long id) {
        Role role = roleMapper.selectById(id);
        if (role == null) {
            throw new BusinessException(404, "角色不存在(id=" + id + ")");
        }
        return role;
    }

    @Override
    @Transactional
    public Role createRole(String roleCode, String roleName) {
        // 校验角色编码唯一性
        Long count = roleMapper.selectCount(
                new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, roleCode));
        if (count > 0) {
            throw new BusinessException(400, "角色编码 " + roleCode + " 已存在");
        }

        Role role = new Role();
        role.setRoleCode(roleCode);
        role.setRoleName(roleName);
        roleMapper.insert(role);

        // 操作日志
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("权限管理");
        log.setOperation("新增角色 " + roleCode + "(" + roleName + ")");
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);

        return role;
    }

    @Override
    @Transactional
    public void deleteRole(Long id) {
        Role role = getRoleById(id);

        // 保护系统内置角色（SUPER_ADMIN / EDU_ADMIN / FINANCE / TEACHER / PARENT）
        if (isSystemRole(role.getRoleCode())) {
            throw new BusinessException(400, "系统内置角色 " + role.getRoleCode() + " 不可删除");
        }

        // 级联清除该角色的权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleCode, role.getRoleCode()));

        // 逻辑删除角色
        roleMapper.deleteById(id);

        // 操作日志
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("权限管理");
        log.setOperation("删除角色 " + role.getRoleCode() + "(" + role.getRoleName() + ")");
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }

    /** 判断是否为系统内置角色（受保护不可删除） */
    private boolean isSystemRole(String roleCode) {
        return "SUPER_ADMIN".equals(roleCode)
                || "EDU_ADMIN".equals(roleCode)
                || "FINANCE".equals(roleCode)
                || "TEACHER".equals(roleCode)
                || "PARENT".equals(roleCode);
    }

    @Override
    public List<String> getRolePermissions(String roleCode) {
        return rolePermissionMapper.selectList(
                new LambdaQueryWrapper<RolePermission>().eq(RolePermission::getRoleCode, roleCode))
                .stream()
                .map(RolePermission::getPermissionCode)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void updateRolePermissions(String roleCode, List<String> permissionCodes) {
        // 先删除旧权限
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleCode, roleCode));
        // 再批量插入新权限
        for (String code : permissionCodes) {
            RolePermission rp = new RolePermission();
            rp.setRoleCode(roleCode);
            rp.setPermissionCode(code);
            rolePermissionMapper.insert(rp);
        }

        // 操作日志：修改角色权限
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("权限管理");
        log.setOperation("修改角色" + roleCode + "权限，权限数量：" + permissionCodes.size());
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
