package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.mapper.MenuMapper;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MenuServiceImpl implements MenuService {

    private final MenuMapper menuMapper;
    private final PermissionMapper permissionMapper;
    private final OperationLogService operationLogService;

    @Override
    public List<Menu> treeList() {
        // 一次查全表
        List<Menu> all = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().orderByAsc(Menu::getSortOrder));
        // 按 parentId 分组
        Map<Long, List<Menu>> childrenMap = all.stream()
                .filter(m -> m.getParentId() != null && m.getParentId() > 0)
                .collect(Collectors.groupingBy(Menu::getParentId));
        // 只返回顶级节点，子节点挂在 children 里需要在返回后由前端构建
        // 这里直接返回全部列表，前端通过 parentId 自行构建树
        return all;
    }

    @Override
    public Menu getById(Long id) {
        Menu menu = menuMapper.selectById(id);
        if (menu == null) {
            throw new BusinessException(404, "菜单不存在(id=" + id + ")");
        }
        return menu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Menu create(Menu menu) {
        // 若有权限码，校验在 permission 表中存在
        if (menu.getPermissionCode() != null && !menu.getPermissionCode().isBlank()) {
            Long count = permissionMapper.selectCount(
                    new LambdaQueryWrapper<Permission>()
                            .eq(Permission::getPermissionCode, menu.getPermissionCode()));
            if (count == 0) {
                throw new BusinessException(400, "权限码不存在：" + menu.getPermissionCode());
            }
        }
        if (menu.getParentId() == null) menu.setParentId(0L);
        if (menu.getSortOrder() == null) menu.setSortOrder(0);
        if (menu.getVisible() == null) menu.setVisible(1);
        // H1/M3 fix: 校验父菜单合法性（存在性 + 非自引用）
        validateParentId(menu.getId(), menu.getParentId());
        menuMapper.insert(menu);

        operationLogService.log("菜单管理", "新增菜单：" + menu.getMenuName());

        return menu;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Menu update(Menu menu) {
        Menu existing = getById(menu.getId());
        if (menu.getPermissionCode() != null && !menu.getPermissionCode().isBlank()
                && !menu.getPermissionCode().equals(existing.getPermissionCode())) {
            Long count = permissionMapper.selectCount(
                    new LambdaQueryWrapper<Permission>()
                            .eq(Permission::getPermissionCode, menu.getPermissionCode()));
            if (count == 0) {
                throw new BusinessException(400, "权限码不存在：" + menu.getPermissionCode());
            }
        }
        // H1/M3 fix: 仅在请求携带 parentId 时校验（部分更新不传则不变更父节点），
        // 防止自引用/移到自身子树下形成环（环上菜单从所有侧边栏消失且 delete 递归 StackOverflow 永久无法删除）
        if (menu.getParentId() != null) {
            validateParentId(menu.getId(), menu.getParentId());
        }
        menuMapper.updateById(menu);

        operationLogService.log("菜单管理", "编辑菜单：" + menu.getMenuName());

        return getById(menu.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Menu menu = getById(id);
        // 递归删除所有子节点
        deleteChildren(menu.getId(), new HashSet<>());
        // 删除自身
        menuMapper.deleteById(id);

        operationLogService.log("菜单管理", "删除菜单：" + menu.getMenuName() + "（含子节点）");
    }

    /**
     * H1/M3 fix: 校验父菜单合法性。
     * - parentId 为 null/0 视为根节点，放行；
     * - 不允许以自身为父（自引用）；
     * - 父菜单必须存在（selectById 遵循 @TableLogic，逻辑删除的父视为不存在）；
     * - 从候选父节点向上回溯，若经过 menuId 本身，说明是把菜单移到自己的子树下，会形成循环引用，拒绝。
     */
    private void validateParentId(Long menuId, Long parentId) {
        if (parentId == null || parentId == 0L) {
            return;
        }
        if (menuId != null && parentId.equals(menuId)) {
            throw new BusinessException(400, "父菜单不能是菜单自身");
        }
        Menu parent = menuMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException(400, "父菜单不存在(id=" + parentId + ")");
        }
        if (menuId != null) {
            Set<Long> visited = new HashSet<>();
            Long cur = parentId;
            while (cur != null && cur != 0L) {
                if (cur.equals(menuId)) {
                    throw new BusinessException(400, "不能将菜单移动到自身或其子菜单下（会形成循环引用）");
                }
                if (!visited.add(cur)) {
                    break; // 安全阀：历史脏数据已成环时避免无限回溯
                }
                Menu m = menuMapper.selectById(cur);
                if (m == null) {
                    break;
                }
                cur = m.getParentId();
            }
        }
    }

    /** 递归删除指定 parentId 的所有子节点（A1#3 fix: visited 集防止历史脏数据成环导致栈溢出） */
    private void deleteChildren(Long parentId, Set<Long> visited) {
        List<Menu> children = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, parentId));
        for (Menu child : children) {
            if (!visited.add(child.getId())) continue;
            deleteChildren(child.getId(), visited);
            menuMapper.deleteById(child.getId());
        }
    }
}
