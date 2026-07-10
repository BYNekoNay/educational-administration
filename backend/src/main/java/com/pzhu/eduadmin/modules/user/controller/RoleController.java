package com.pzhu.eduadmin.modules.user.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.user.entity.Role;
import com.pzhu.eduadmin.modules.user.service.RoleService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 角色与权限配置。对应 docs/09-接口规范.md §2 鉴权模块。
 */
@RestController
@RequestMapping("/api/admin/roles")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN"})
public class RoleController {

    private final RoleService roleService;

    @GetMapping
    public Result<List<Role>> list() {
        return Result.success(roleService.listRoles());
    }

    // ---- 规范路径：{id} ----

    @GetMapping("/{id}/permissions")
    public Result<Map<String, Object>> getPermissionsById(@PathVariable Long id) {
        Role role = findRoleById(id);
        List<String> codes = roleService.getRolePermissions(role.getRoleCode());
        return Result.success(Map.of("roleId", id, "roleCode", role.getRoleCode(), "permissionCodes", codes));
    }

    @PutMapping("/{id}/permissions")
    public Result<Void> updatePermissionsById(@PathVariable Long id, @RequestBody List<String> permissionCodes) {
        Role role = findRoleById(id);
        roleService.updateRolePermissions(role.getRoleCode(), permissionCodes);
        return Result.success();
    }

    @PostMapping
    public Result<Role> create(@RequestBody Map<String, String> body) {
        String roleCode = body.get("roleCode");
        String roleName = body.get("roleName");
        if (roleCode == null || roleCode.isBlank() || roleName == null || roleName.isBlank()) {
            throw new BusinessException(400, "角色编码和角色名称不能为空");
        }
        return Result.success(roleService.createRole(roleCode.trim(), roleName.trim()));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        roleService.deleteRole(id);
        return Result.success();
    }

    // ---- 兼容旧路径：{roleCode} ----

    @Deprecated
    @GetMapping("/code/{roleCode}/permissions")
    public Result<Map<String, Object>> getPermissions(@PathVariable String roleCode) {
        List<String> codes = roleService.getRolePermissions(roleCode);
        return Result.success(Map.of("roleCode", roleCode, "permissionCodes", codes));
    }

    @Deprecated
    @PutMapping("/code/{roleCode}/permissions")
    public Result<Void> updatePermissions(@PathVariable String roleCode, @RequestBody List<String> permissionCodes) {
        roleService.updateRolePermissions(roleCode, permissionCodes);
        return Result.success();
    }

    private Role findRoleById(Long id) {
        List<Role> roles = roleService.listRoles();
        return roles.stream()
                .filter(r -> r.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "角色不存在(id=" + id + ")"));
    }
}
