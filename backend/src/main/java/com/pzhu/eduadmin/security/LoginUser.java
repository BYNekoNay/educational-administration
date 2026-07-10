package com.pzhu.eduadmin.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 当前登录用户上下文，由 JwtInterceptor 解析 Token 后写入，
 * Controller/Service 通过 CurrentUserHolder 读取。
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String roleCode;
}
