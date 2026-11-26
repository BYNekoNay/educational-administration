package com.pzhu.eduadmin.modules.auth.controller;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.auth.dto.ChangePasswordRequest;
import com.pzhu.eduadmin.modules.auth.service.IAuthService;
import com.pzhu.eduadmin.modules.user.dto.*;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 登录、退出、当前用户信息。对应 docs/09-接口规范.md 鉴权模块、docs/11-后端开发详细文档.md §2。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final IAuthService authService;
    private final UserMapper userMapper;
    private final com.pzhu.eduadmin.security.StreamTicketService streamTicketService;
    private final com.pzhu.eduadmin.security.DownloadTicketService downloadTicketService;

    /** 导出直链可用端点白名单：票据不可签发到鉴权路径之外 */
    private static final java.util.Set<String> DOWNLOAD_ENDPOINTS = java.util.Set.of(
            "/api/export/payments", "/api/export/lesson-flows",
            "/api/export/salaries", "/api/export/risk-students");

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    /**
     * 为 SSE 换取一次性票据（60 秒、单次使用）。
     *
     * <p>EventSource 不支持自定义 Header，凭证只能进 URL。用它替代把长有效期 JWT
     * 直接拼到查询串——后者会落进 nginx 日志/浏览器历史/Referer。</p>
     */
    @PostMapping("/stream-ticket")
    public Result<java.util.Map<String, Object>> streamTicket() {
        LoginUser user = CurrentUserHolder.get();
        String ticket = streamTicketService.issue(user.getUserId());
        return Result.success(java.util.Map.of("ticket", ticket, "expiresIn", 60));
    }

    /**
     * 为导出直链换一次性下载票据（60 秒、单次使用、绑定端点）。
     *
     * <p>&lt;a href&gt; 原生下载无法带 Authorization 头，先用正常 Bearer 鉴权换票据，
     * 再把票据拼到导出 URL 的 query（downloadTicket=）走浏览器原生下载通道，
     * 绕开 blob 触发的浏览器 Office 下载扫描（.crdownload 卡住）。
     * JwtInterceptor 对 /api/export/** 识别票据并注入用户，@RequireRole 照常生效。</p>
     */
    @PostMapping("/download-ticket")
    public Result<java.util.Map<String, Object>> downloadTicket(@RequestBody java.util.Map<String, String> body) {
        String endpoint = body.get("endpoint");
        if (endpoint == null || !DOWNLOAD_ENDPOINTS.contains(endpoint)) {
            throw new com.pzhu.eduadmin.common.BusinessException(400, "不支持的导出端点");
        }
        LoginUser user = CurrentUserHolder.get();
        String ticket = downloadTicketService.issue(user.getUserId(), endpoint);
        return Result.success(java.util.Map.of("ticket", ticket, "expiresIn", 60));
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
    public Result<Void> logout() {        // 登出时原子递增 version，使该用户所有现有 Token 立即失效
        LoginUser loginUser = CurrentUserHolder.get();
        if (loginUser != null) {
            userMapper.update(null, new LambdaUpdateWrapper<User>()
                    .eq(User::getId, loginUser.getUserId())
                    .setSql("version = version + 1"));
        }
        return Result.success();
    }

    /**
     * 本人修改密码。
     * <p>userId 一律取自 CurrentUserHolder，不接受前端传入——防止"改他人密码"。
     * 成功后返回新 token：本机继续使用，其他设备的旧 token 因 version 变更立即失效。
     */
    @PutMapping("/password")
    public Result<LoginResponse> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(authService.changePassword(loginUser.getUserId(), request));
    }

    /** 获取当前用户可见菜单树（按权限过滤），前端侧栏动态渲染使用 */
    @GetMapping("/menus")
    public Result<List<MenuTreeNode>> myMenus() {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(authService.getMyMenus(loginUser.getRoleCode()));
    }
}
