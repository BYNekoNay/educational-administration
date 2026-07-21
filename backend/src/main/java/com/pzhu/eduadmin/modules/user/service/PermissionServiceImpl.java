package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final OperationLogService operationLogService;

    @Override
    public List<Permission> listAll() {
        return permissionMapper.selectList(new LambdaQueryWrapper<>());
    }

    @Override
    public Permission getById(Long id) {
        Permission p = permissionMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "权限不存在(id=" + id + ")");
        }
        return p;
    }

    @Override
    public Permission create(Permission permission) {
        // 校验权限码唯一性
        Long count = permissionMapper.selectCount(
                new LambdaQueryWrapper<Permission>()
                        .eq(Permission::getPermissionCode, permission.getPermissionCode()));
        if (count > 0) {
            throw new BusinessException(400, "权限码已存在：" + permission.getPermissionCode());
        }
        permissionMapper.insert(permission);

        operationLogService.log("权限管理", "新增菜单权限：" + permission.getPermissionCode() + " → " + permission.getPath());

        return permission;
    }

    @Override
    public Permission update(Permission permission) {
        Permission existing = getById(permission.getId());
        // 如果修改了权限码，校验唯一性
        if (!existing.getPermissionCode().equals(permission.getPermissionCode())) {
            Long count = permissionMapper.selectCount(
                    new LambdaQueryWrapper<Permission>()
                            .eq(Permission::getPermissionCode, permission.getPermissionCode()));
            if (count > 0) {
                throw new BusinessException(400, "权限码已存在：" + permission.getPermissionCode());
            }
            // H2 fix: 级联更新 role_permission 中的旧权限码，防止关联孤立
            rolePermissionMapper.update(null, new LambdaUpdateWrapper<RolePermission>()
                    .eq(RolePermission::getPermissionCode, existing.getPermissionCode())
                    .set(RolePermission::getPermissionCode, permission.getPermissionCode()));
        }
        permissionMapper.updateById(permission);

        operationLogService.log("权限管理", "编辑菜单权限：" + permission.getPermissionCode() + " → " + permission.getPath());

        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Permission permission = getById(id);
        // 级联删除角色-权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getPermissionCode, permission.getPermissionCode()));
        // 删除权限定义
        permissionMapper.deleteById(id);

        operationLogService.log("权限管理", "删除菜单权限：" + permission.getPermissionCode());
    }
}
