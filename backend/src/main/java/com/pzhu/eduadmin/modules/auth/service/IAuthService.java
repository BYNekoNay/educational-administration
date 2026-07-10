package com.pzhu.eduadmin.modules.auth.service;

import com.pzhu.eduadmin.modules.user.dto.CurrentUserResponse;
import com.pzhu.eduadmin.modules.user.dto.LoginRequest;
import com.pzhu.eduadmin.modules.user.dto.LoginResponse;

/**
 * 登录鉴权服务接口。对应 docs/11-后端开发详细文档.md §2。
 */
public interface IAuthService {

    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录用户的个人信息，对应 docs/09-接口规范.md GET /api/auth/profile。
     */
    CurrentUserResponse profile(Long userId);
}
