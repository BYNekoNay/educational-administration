package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.Role;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RoleMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
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
    private final UserMapper userMapper;

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
    @Transactional(rollbackFor = Exception.class)
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
        com.pzhu.eduadmin.security.LoginUser opLog = CurrentUserHolder.get(); log.setOperatorId(opLog != null ? opLog.getUserId() : 0L);
        log.setModule("权限管理");
        log.setOperation("新增角色 " + roleCode + "(" + roleName + ")");
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);

        return role;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRole(Long id, String roleName) {
        Role role = getRoleById(id);

        // 保护系统内置角色不可修改名称
        if (isSystemRole(role.getRoleCode())) {
            throw new BusinessException(400, "系统内置角色 " + role.getRoleCode() + " 不可修改");
        }

        role.setRoleName(roleName);
        roleMapper.updateById(role);

        // 操作日志
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser opLog = CurrentUserHolder.get(); log.setOperatorId(opLog != null ? opLog.getUserId() : 0L);
        log.setModule("权限管理");
        log.setOperation("编辑角色 " + role.getRoleCode() + " 名称为 " + roleName);
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteRole(Long id) {
        Role role = getRoleById(id);

        // 保护系统内置角色（SUPER_ADMIN / EDU_ADMIN / FINANCE / TEACHER / PARENT）
        if (isSystemRole(role.getRoleCode())) {
            throw new BusinessException(400, "系统内置角色 " + role.getRoleCode() + " 不可删除");
        }

        // 检查角色下是否还有关联用户
        Long userCount = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getRoleCode, role.getRoleCode()));
        if (userCount > 0) {
            throw new BusinessException(409, "该角色下仍有 " + userCount + " 个用户，请先迁移用户后再删除");
        }

        // 级联清除该角色的权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getRoleCode, role.getRoleCode()));

        // 物理删除角色（绕过 @TableLogic 避免 role_code 唯一键冲突）
        roleMapper.realDeleteByRoleCode(role.getRoleCode());

        // 操作日志
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser opLog = CurrentUserHolder.get(); log.setOperatorId(opLog != null ? opLog.getUserId() : 0L);
        log.setModule("权限管理");
        log.setOperation("删除角色 " + role.getRoleCode() + "(" + role.getRoleName() + ")");
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
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
    @Transactional(rollbackFor = Exception.class)
    public void updateRolePermissions(String roleCode, List<String> permissionCodes) {
        // 先物理删除旧权限（绕过 @TableLogic，避免逻辑删除行持续占用唯一键导致重建时 409）
        rolePermissionMapper.realDeleteByRoleCode(roleCode);
        // 再批量插入新权限
        for (String code : permissionCodes) {
            RolePermission rp = new RolePermission();
            rp.setRoleCode(roleCode);
            rp.setPermissionCode(code);
            rolePermissionMapper.insert(rp);
        }

        // 操作日志：修改角色权限
        OperationLog log = new OperationLog();
        com.pzhu.eduadmin.security.LoginUser opLog = CurrentUserHolder.get(); log.setOperatorId(opLog != null ? opLog.getUserId() : 0L);
        log.setModule("权限管理");
        log.setOperation("修改角色" + roleCode + "权限，权限数量：" + permissionCodes.size());
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);
    }
}
