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

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        String token = null;
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        // SSE EventSource 不支持自定义 Header，仅 /api/notifications/stream 兼容 query param 传 token
        if (token == null || token.isEmpty()) {
            String uri = request.getRequestURI();
            if (uri != null && uri.startsWith("/api/notifications/stream")) {
                token = request.getParameter("token");
            }
        }
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
        String username = claims.get("username", String.class);
        String roleCode = claims.get("roleCode", String.class);
        Integer tokenVersion = claims.get("version", Integer.class);

        // Token 吊销检查：比对 User.version
        User currentUser = userMapper.selectOne(
                new LambdaQueryWrapper<User>()
                        .select(User::getVersion, User::getStatus, User::getRoleCode)
                        .eq(User::getId, userId));
        if (currentUser == null) {
            writeUnauthorized(response, "用户不存在，请重新登录");
            return false;
        }
        if (currentUser.getStatus() != null && currentUser.getStatus() != 1) {
            writeUnauthorized(response, "账号已被禁用，请联系管理员");
            return false;
        }
        int dbVersion = currentUser.getVersion() != null ? currentUser.getVersion() : 0;
        int tkVersion = tokenVersion != null ? tokenVersion : 0;
        if (dbVersion != tkVersion) {
            writeUnauthorized(response, "登录已失效，请重新登录");
            return false;
        }

        // Bug #46: 使用数据库中的 roleCode 而非 Token 中的，确保角色变更后立即生效
        String dbRoleCode = currentUser.getRoleCode();
        CurrentUserHolder.set(new LoginUser(userId, username, dbRoleCode));

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null && (dbRoleCode == null || Arrays.stream(requireRole.value()).noneMatch(dbRoleCode::equals))) {
            writeForbidden(response, "无权访问该接口");
            return false;
        }

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
