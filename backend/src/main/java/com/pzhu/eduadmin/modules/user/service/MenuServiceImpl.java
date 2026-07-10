package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.mapper.MenuMapper;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
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
    private final OperationLogMapper operationLogMapper;

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
    @Transactional
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
        menuMapper.insert(menu);

        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("菜单管理");
        log.setOperation("新增菜单：" + menu.getMenuName());
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);

        return menu;
    }

    @Override
    @Transactional
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
        menuMapper.updateById(menu);

        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("菜单管理");
        log.setOperation("编辑菜单：" + menu.getMenuName());
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);

        return getById(menu.getId());
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Menu menu = getById(id);
        // 递归删除所有子节点
        deleteChildren(menu.getId());
        // 删除自身
        menuMapper.deleteById(id);

        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule("菜单管理");
        log.setOperation("删除菜单：" + menu.getMenuName() + "（含子节点）");
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }

    /** 递归删除指定 parentId 的所有子节点 */
    private void deleteChildren(Long parentId) {
        List<Menu> children = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>().eq(Menu::getParentId, parentId));
        for (Menu child : children) {
            deleteChildren(child.getId());
            menuMapper.deleteById(child.getId());
        }
    }
}
