package com.pzhu.eduadmin.modules.auth.controller;

import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.auth.service.IAuthService;
import com.pzhu.eduadmin.modules.user.dto.CurrentUserResponse;
import com.pzhu.eduadmin.modules.user.dto.LoginRequest;
import com.pzhu.eduadmin.modules.user.dto.LoginResponse;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 登录、退出、当前用户信息。对应 docs/09-接口规范.md 鉴权模块、docs/11-后端开发详细文档.md §2。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @GetMapping("/profile")
    public Result<CurrentUserResponse> profile() {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(authService.profile(loginUser.getUserId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // 无状态 JWT：退出登录由前端清除本地 Token 即可，此接口保留用于记录操作日志等扩展
        return Result.success();
    }
}
