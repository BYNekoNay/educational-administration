package com.pzhu.eduadmin.modules.user.controller;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.service.PermissionService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 菜单权限定义管理。仅超级管理员可操作。
 */
@RestController
@RequestMapping("/api/admin/permissions")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN"})
public class PermissionController {

    private final PermissionService permissionService;

    @GetMapping
    public Result<List<Permission>> list() {
        return Result.success(permissionService.listAll());
    }

    @GetMapping("/{id}")
    public Result<Permission> getById(@PathVariable Long id) {
        return Result.success(permissionService.getById(id));
    }

    @PostMapping
    public Result<Permission> create(@Valid @RequestBody Permission permission) {
        // Mass assignment protection: strip server-controlled fields
        permission.setId(null);
        permission.setCreateTime(null);
        permission.setUpdateTime(null);
        return Result.success(permissionService.create(permission));
    }

    @PutMapping("/{id}")
    public Result<Permission> update(@PathVariable Long id, @Valid @RequestBody Permission permission) {
        permission.setId(id);
        // L4 fix: 剥离服务端控制的审计字段，防止客户端覆盖 createTime/updateTime
        permission.setCreateTime(null);
        permission.setUpdateTime(null);
        return Result.success(permissionService.update(permission));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        permissionService.delete(id);
        return Result.success();
    }
}
