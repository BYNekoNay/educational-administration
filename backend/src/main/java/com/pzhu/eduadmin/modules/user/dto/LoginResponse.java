package com.pzhu.eduadmin.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    /** 当前角色拥有的菜单权限码列表（menu:xxx），前端直接用于路由守卫与菜单过滤 */
    private List<String> permissions;

    /** 兼容无权限参数的构造器（权限默认为空列表） */
    public LoginResponse(String token, Long userId, String username, String realName, String roleCode) {
        this(token, userId, username, realName, roleCode, Collections.emptyList());
    }
}
