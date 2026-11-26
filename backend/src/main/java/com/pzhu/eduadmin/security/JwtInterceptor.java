package com.pzhu.eduadmin.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Arrays;

/**
 * 拦截器：解析 Authorization: Bearer {token}，校验并写入 CurrentUserHolder；
 * 若接口标注 @RequireRole，额外校验角色是否在允许范围内。
 * 支持 Token 吊销：通过 User.version 字段比对，禁用用户或变更角色后旧 Token 自动失效。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private DownloadTicketService downloadTicketService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        // 导出直链下载：<a href> 无法带 Authorization 头（与当年 SSE EventSource 同款约束）。
        // 前端先用 Bearer 鉴权调 POST /api/auth/download-ticket 换 60s 一次性票据
        // （绑定端点、取出即删），再拼 query 走浏览器原生下载通道，绕开 blob 的
        // Office 下载扫描卡 .crdownload 问题。票据校验后走与 JWT 完全相同的
        // 用户校验与 @RequireRole 注入（validateAndInject）。
        String uri = request.getRequestURI();
        String downloadTicket = (uri != null && uri.startsWith("/api/export/"))
                ? request.getParameter("downloadTicket") : null;
        if (downloadTicket != null) {
            Long ticketUserId = downloadTicketService.consume(downloadTicket, uri);
            if (ticketUserId == null) {
                writeUnauthorized(response, "下载票据无效或已过期，请重新导出");
                return false;
            }
            User ticketUser = userMapper.selectOne(new LambdaQueryWrapper<User>()
                    .select(User::getId, User::getStatus, User::getRoleCode, User::getMustChangePassword)
                    .eq(User::getId, ticketUserId));
            if (ticketUser == null) {
                writeUnauthorized(response, "用户不存在，请重新登录");
                return false;
            }
            // 票据 60s 窗口 + 单次消费 + 端点绑定；签发侧 Bearer 已做 version 吊销校验，
            // 消费侧 60s 内的吊销延迟窗口极小，接受。角色与强制改密与 JWT 分支共用。
            if (!validateAndInject(request, response, handlerMethod, ticketUser)) {
                return false;
            }
            return true;
        }

        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        // 注意：/api/notifications/stream 已在 WebMvcConfig 里 exclude，不再在这里读 query token。
        // 原先的 ?token=<JWT> 后门会泄漏长有效期凭证（进 nginx 日志/浏览器历史/Referer）
        if (token == null || token.isEmpty()) {
            writeUnauthorized(response, "未登录或登录已过期");
            return false;
        }
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (ExpiredJwtException e) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        } catch (Exception e) {
            writeUnauthorized(response, "登录凭证无效，请重新登录");
            return false;
        }

        Long userId = Long.valueOf(claims.getSubject());
        Integer tokenVersion = claims.get("version", Integer.class);

        // Token 吊销检查：比对 User.version
        User currentUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .select(User::getId, User::getVersion, User::getStatus, User::getRoleCode, User::getMustChangePassword)
                        .eq(User::getId, userId));
        if (currentUser == null) {
            writeUnauthorized(response, "用户不存在，请重新登录");
            return false;
        }
        int dbVersion = currentUser.getVersion() != null ? currentUser.getVersion() : 0;
        int tkVersion = tokenVersion != null ? tokenVersion : 0;
        if (dbVersion != tkVersion) {
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }

        // 状态/角色/强制改密校验 + LoginUser 注入（票据分支复用同一方法）
        if (!validateAndInject(request, response, handlerMethod, currentUser)) {
            return false;
        }

        return true;
    }

    /**
     * 用户状态校验、@RequireRole 角色校验、强制改密白名单、LoginUser 注入。
     * 返回 false 表示已写出错误响应。username 取数据库值（角色/用户名变更即递增
     * version 踢下线，走到此处二者必然一致）。
     */
    private boolean validateAndInject(HttpServletRequest request, HttpServletResponse response,
                                      Object handlerMethod, User currentUser) throws Exception {
        // 与登录逻辑保持一致，status 为 null 视为禁用（原写法 null 会被放行）
        if (!Integer.valueOf(1).equals(currentUser.getStatus())) {
            writeUnauthorized(response, "账号已被禁用，请联系管理员");
            return false;
        }

        // 使用数据库中的 roleCode 而非 Token 中的，确保角色变更后立即生效
        String dbRoleCode = currentUser.getRoleCode();

        RequireRole requireRole = ((HandlerMethod) handlerMethod).getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = ((HandlerMethod) handlerMethod).getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null && (dbRoleCode == null || Arrays.stream(requireRole.value()).noneMatch(dbRoleCode::equals))) {
            writeForbidden(response, "无权访问该接口");
            return false;
        }

        // 强制改密拦截：密码非本人设置（管理员代设初始密码、或管理员重置过）时，
        // 只放行改密与鉴权自检这 4 个接口。
        //
        // 【位置约束】必须在下方 CurrentUserHolder.set() 之前 return。
        //   preHandle 返回 false 时 Spring 不会调用 afterCompletion，
        //   若在 set 之后 return，LoginUser 会泄漏到线程池工作线程。
        // 【白名单约束】命中白名单时**不能**在这里 return，必须继续走到 set()，
        //   否则 /api/auth/password 里 CurrentUserHolder.get() 直接 NPE，
        //   而且只会在"未改密用户点改密"这一条路径复现。
        if (Integer.valueOf(1).equals(currentUser.getMustChangePassword())) {
            String reqUri = request.getRequestURI();
            boolean whitelisted = "/api/auth/password".equals(reqUri)
                    || "/api/auth/logout".equals(reqUri)
                    || "/api/auth/profile".equals(reqUri)
                    || "/api/auth/menus".equals(reqUri);
            if (!whitelisted) {
                writeForbidden(response, "NEED_CHANGE_PASSWORD");
                return false;
            }
        }

        // 仅在鉴权完全通过后再设置 ThreadLocal。若在角色校验前设置，校验失败 return false 时
        // Spring 不会调用本拦截器的 afterCompletion，CurrentUserHolder.clear 永不执行 → LoginUser 泄漏到线程池工作线程。
        CurrentUserHolder.set(new LoginUser(currentUser.getId(), currentUser.getUsername(), dbRoleCode));

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        CurrentUserHolder.clear();
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        writeJson(response, 401, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        writeJson(response, 403, message);
    }

    private void writeJson(HttpServletResponse response, int code, String message) throws Exception {
        response.setStatus(code);
        response.setContentType("application/json;charset=UTF-8");
        // 转义 message 中的特殊字符，防止 JSON 注入
        String safeMessage = message.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
        response.getWriter().write("{\"code\":" + code + ",\"message\":\"" + safeMessage + "\",\"data\":null}");
    }
}
