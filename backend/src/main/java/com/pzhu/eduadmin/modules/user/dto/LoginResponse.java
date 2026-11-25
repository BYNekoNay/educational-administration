package com.pzhu.eduadmin.modules.user.dto;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
    /** 当前角色拥有的菜单权限码列表（menu:xxx），前端直接用于路由守卫与菜单过滤 */
    private List<String> permissions;
    /**
     * 是否必须修改密码（密码非本人设置）。
     * <p>为 true 时前端应立即跳转改密页；后端也会在拦截器中强制拦截，
     * 前端跳转只是体验优化，不是安全边界。
     */
    private Boolean mustChangePassword;

    /** 全参构造器（手写，不用 @AllArgsConstructor，以免破坏下方兼容构造器） */
    public LoginResponse(String token, Long userId, String username, String realName,
                         String roleCode, List<String> permissions, Boolean mustChangePassword) {
        this.token = token;
        this.userId = userId;
        this.username = username;
        this.realName = realName;
        this.roleCode = roleCode;
        this.permissions = permissions;
        this.mustChangePassword = mustChangePassword;
    }

    /** 兼容无改密标志的调用点 */
    public LoginResponse(String token, Long userId, String username, String realName,
                         String roleCode, List<String> permissions) {
        this(token, userId, username, realName, roleCode, permissions, Boolean.FALSE);
    }

    /** 兼容无权限参数的构造器（权限默认为空列表） */
    public LoginResponse(String token, Long userId, String username, String realName, String roleCode) {
        this(token, userId, username, realName, roleCode, Collections.emptyList(), Boolean.FALSE);
    }
}
