package com.pzhu.eduadmin.modules.user.service;

import com.pzhu.eduadmin.modules.user.entity.Permission;

import java.util.List;

public interface PermissionService {

    List<Permission> listAll();

    Permission getById(Long id);

    Permission create(Permission permission);

    Permission update(Permission permission);

    void delete(Long id);
}
