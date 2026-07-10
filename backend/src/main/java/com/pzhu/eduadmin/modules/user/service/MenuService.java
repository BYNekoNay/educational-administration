package com.pzhu.eduadmin.modules.user.service;

import com.pzhu.eduadmin.modules.user.entity.Menu;

import java.util.List;

public interface MenuService {

    /** 返回树形菜单列表 */
    List<Menu> treeList();

    Menu getById(Long id);

    Menu create(Menu menu);

    Menu update(Menu menu);

    void delete(Long id);
}
