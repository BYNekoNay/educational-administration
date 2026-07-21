package com.pzhu.eduadmin.modules.auth.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.auth.service.IAuthService;
import com.pzhu.eduadmin.modules.user.dto.CurrentUserResponse;
import com.pzhu.eduadmin.modules.user.dto.LoginRequest;
import com.pzhu.eduadmin.modules.user.dto.LoginResponse;
import com.pzhu.eduadmin.modules.user.dto.RegisterRequest;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
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
    private final UserMapper userMapper;

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request) {
        return Result.success(authService.register(request));
    }

    @GetMapping("/profile")
    public Result<CurrentUserResponse> profile() {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(authService.profile(loginUser.getUserId()));
    }

    @PostMapping("/logout")
    public Result<Void> logout() {
        // C6 fix: 登出时原子递增 version，使该用户所有现有 Token 立即失效
        LoginUser loginUser = CurrentUserHolder.get();
        if (loginUser != null) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, loginUser.getUserId())
                    .setSql("version = version + 1"));
        }
        return Result.success();
    }
}
