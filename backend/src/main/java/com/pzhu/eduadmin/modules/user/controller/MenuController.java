package com.pzhu.eduadmin.modules.user.controller;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.service.MenuService;
import com.pzhu.eduadmin.security.RequireRole;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 系统菜单树管理。仅超级管理员可操作。
 */
@RestController
@RequestMapping("/api/admin/menus")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN"})
public class MenuController {

    private final MenuService menuService;

    /** 返回树形结构：顶级节点包含 children */
    @GetMapping("/tree")
    public Result<List<Map<String, Object>>> tree() {
        List<Menu> all = menuService.treeList();
        // 构建树形结构
        Map<Long, List<Map<String, Object>>> childrenMap = new HashMap<>();
        List<Map<String, Object>> topNodes = new ArrayList<>();

        for (Menu m : all) {
            Map<String, Object> node = toMap(m);
            childrenMap.computeIfAbsent(m.getParentId(), k -> new ArrayList<>()).add(node);
        }

        for (Map.Entry<Long, List<Map<String, Object>>> entry : childrenMap.entrySet()) {
            for (Map<String, Object> node : entry.getValue()) {
                Long id = ((Number) node.get("id")).longValue();
                List<Map<String, Object>> children = childrenMap.get(id);
                if (children != null) {
                    node.put("children", children);
                }
            }
        }

        return Result.success(childrenMap.getOrDefault(0L, Collections.emptyList()));
    }

    @GetMapping("/{id}")
    public Result<Menu> getById(@PathVariable Long id) {
        return Result.success(menuService.getById(id));
    }

    @PostMapping
    public Result<Menu> create(@RequestBody Menu menu) {
        return Result.success(menuService.create(menu));
    }

    @PutMapping("/{id}")
    public Result<Menu> update(@PathVariable Long id, @RequestBody Menu menu) {
        menu.setId(id);
        return Result.success(menuService.update(menu));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        menuService.delete(id);
        return Result.success();
    }

    private Map<String, Object> toMap(Menu m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("parentId", m.getParentId());
        map.put("menuName", m.getMenuName());
        map.put("icon", m.getIcon());
        map.put("path", m.getPath());
        map.put("permissionCode", m.getPermissionCode());
        map.put("sortOrder", m.getSortOrder());
        map.put("visible", m.getVisible());
        return map;
    }
}
