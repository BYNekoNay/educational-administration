package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.mapper.MenuMapper;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionMapper permissionMapper;
    private final RolePermissionMapper rolePermissionMapper;
    private final MenuMapper menuMapper;
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
    @Transactional(rollbackFor = Exception.class)
    public Permission update(Permission permission) {
        Permission existing = getById(permission.getId());
        // 如果修改了权限码，校验唯一性
        // fix: 仅在请求确实携带新权限码时才进入改名分支，防止部分更新传 null 把关联权限码级联成 NULL
        if (permission.getPermissionCode() != null
                && !java.util.Objects.equals(existing.getPermissionCode(), permission.getPermissionCode())) {
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
            // M4 fix: 同步级联 sys_menu.permission_code，否则菜单残留旧码，
            // 非超管角色按权限集过滤菜单时该菜单（及其目录子树）会从所有侧边栏消失
            menuMapper.update(null, new LambdaUpdateWrapper<Menu>()
                    .eq(Menu::getPermissionCode, existing.getPermissionCode())
                    .set(Menu::getPermissionCode, permission.getPermissionCode()));
        }
        permissionMapper.updateById(permission);

        try {
            operationLogService.log("权限管理", "编辑菜单权限：" + permission.getPermissionCode() + " → " + permission.getPath());
        } catch (Exception e) {
            log.warn("操作日志记录失败: {}", e.getMessage());
        }

        return permission;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Permission permission = getById(id);
        // M5 fix: 若有菜单仍引用该权限码，删除会使菜单 permission_code 悬空，
        // 非超管角色按权限集过滤时该菜单会从侧边栏消失。先校验再删除。
        Long menuRefs = menuMapper.selectCount(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getPermissionCode, permission.getPermissionCode()));
        if (menuRefs != null && menuRefs > 0) {
            throw new BusinessException(409, "该权限码仍被 " + menuRefs + " 个菜单引用，请先解除菜单关联再删除");
        }
        // 级联删除角色-权限关联
        rolePermissionMapper.delete(new LambdaQueryWrapper<RolePermission>()
                .eq(RolePermission::getPermissionCode, permission.getPermissionCode()));
        // 删除权限定义
        permissionMapper.deleteById(id);

        operationLogService.log("权限管理", "删除菜单权限：" + permission.getPermissionCode());
    }
}
