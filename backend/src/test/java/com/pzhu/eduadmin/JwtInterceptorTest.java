package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.JwtInterceptor;
import com.pzhu.eduadmin.security.JwtUtil;
import com.pzhu.eduadmin.security.RequireRole;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.impl.DefaultClaims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.method.HandlerMethod;

import java.io.PrintWriter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JWT 拦截器单元测试")
class JwtInterceptorTest {

    @Mock private JwtUtil jwtUtil;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private JwtInterceptor jwtInterceptor;

    @Mock private HttpServletRequest request;
    @Mock private HttpServletResponse response;
    @Mock private PrintWriter writer;
    @Mock private HandlerMethod handlerMethod;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, ""), User.class);
    }

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(response.getWriter()).thenReturn(writer);
    }

    @Test
    @DisplayName("handler 不是 HandlerMethod — 直接放行")
    void handlerNotHandlerMethod() throws Exception {
        boolean result = jwtInterceptor.preHandle(request, response, new Object());

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("无 Authorization header — 401")
    void noAuthHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(401);
    }

    @Test
    @DisplayName("Authorization 不以 Bearer 开头 — 401")
    void invalidAuthHeader() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Basic xxx");

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Token 过期 — 401")
    void expiredToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer expired.token.here");
        when(jwtUtil.parseToken(anyString())).thenThrow(new ExpiredJwtException(null, null, "expired"));

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Token 解析异常 — 401")
    void invalidToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer invalid");
        when(jwtUtil.parseToken(anyString())).thenThrow(new RuntimeException("parse error"));

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("用户不存在 — 401")
    void userNotFound() throws Exception {
        mockValidToken("1", "admin", "SUPER_ADMIN", 0);
        when(userMapper.selectOne(any())).thenReturn(null);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("用户状态非 1 — 401")
    void userDisabled() throws Exception {
        mockValidToken("1", "admin", "SUPER_ADMIN", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(0);
        when(userMapper.selectOne(any())).thenReturn(u);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Token version 与 DB version 不一致 — 401 吊销检查")
    void versionMismatch() throws Exception {
        mockValidToken("1", "admin", "SUPER_ADMIN", 0);
        User u = new User();
        u.setVersion(1); // DB version 更高
        u.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(u);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("正常通过无 RequireRole — 返回 true")
    void passNoRequireRole() throws Exception {
        mockValidToken("1", "admin", "SUPER_ADMIN", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("RequireRole 角色匹配 — 通过")
    void requireRole_Match() throws Exception {
        mockValidToken("1", "admin", "SUPER_ADMIN", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        u.setRoleCode("SUPER_ADMIN");
        when(userMapper.selectOne(any())).thenReturn(u);

        // 模拟 @RequireRole({"SUPER_ADMIN"})
        when(handlerMethod.getMethodAnnotation(RequireRole.class))
                .thenReturn(createRequireRole("SUPER_ADMIN"));

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("RequireRole 角色不匹配 — 403")
    void requireRole_NoMatch() throws Exception {
        mockValidToken("1", "teacher", "TEACHER", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        u.setRoleCode("TEACHER");
        when(userMapper.selectOne(any())).thenReturn(u);

        when(handlerMethod.getMethodAnnotation(RequireRole.class))
                .thenReturn(createRequireRole("SUPER_ADMIN"));

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);

        assertThat(result).isFalse();
        verify(response).setStatus(403);
    }

    @Test
    @DisplayName("afterCompletion 清除 CurrentUserHolder")
    void afterCompletion() throws Exception {
        jwtInterceptor.afterCompletion(request, response, handlerMethod, null);
        // 验证 CurrentUserHolder 已清除
        assertThat(com.pzhu.eduadmin.security.CurrentUserHolder.get()).isNull();
    }

    @Test
    @DisplayName("writeJson 输出格式正确的 JSON")
    void writeJsonOutputsValidJson() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);

        java.io.StringWriter sw = new java.io.StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        jwtInterceptor.preHandle(request, response, handlerMethod);

        String json = sw.toString();
        assertThat(json).startsWith("{");
        assertThat(json).endsWith("}");
        assertThat(json).contains("\"code\":401");
        assertThat(json).contains("\"data\":null");
    }

    @Test
    @DisplayName("writeJson 403 响应格式正确")
    void writeJson403Format() throws Exception {
        mockValidToken("1", "teacher", "TEACHER", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        u.setRoleCode("TEACHER");
        when(userMapper.selectOne(any())).thenReturn(u);
        when(handlerMethod.getMethodAnnotation(RequireRole.class))
                .thenReturn(createRequireRole("SUPER_ADMIN"));

        java.io.StringWriter sw = new java.io.StringWriter();
        when(response.getWriter()).thenReturn(new PrintWriter(sw));

        jwtInterceptor.preHandle(request, response, handlerMethod);

        String json = sw.toString();
        assertThat(json).contains("\"code\":403");
        assertThat(json).contains("\"message\":\"无权访问该接口\"");
    }

    @Test
    @DisplayName("SSE 通知流接受 query param token")
    void sseStreamAcceptsQueryToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/notifications/stream");
        when(request.getParameter("token")).thenReturn("valid.token");
        mockClaims("1", "admin", "SUPER_ADMIN", 0);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("普通接口拒绝 query param token（仅 SSE 可用）")
    void normalPathRejectsQueryToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/edu/students");

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);
        assertThat(result).isFalse();
        verify(response).setStatus(401);
        // 非 SSE 路径，不得尝试读取 query param
        verify(request, never()).getParameter("token");
    }

    @Test
    @DisplayName("Authorization Header 优先于 query param token")
    void authHeaderOverridesQueryToken() throws Exception {
        when(request.getHeader("Authorization")).thenReturn("Bearer header.token");
        DefaultClaims claims = new DefaultClaims();
        claims.setSubject("1");
        claims.put("username", "admin");
        claims.put("roleCode", "SUPER_ADMIN");
        claims.put("version", 0);
        when(jwtUtil.parseToken("header.token")).thenReturn(claims);
        User u = new User();
        u.setVersion(0);
        u.setStatus(1);
        when(userMapper.selectOne(any())).thenReturn(u);
        when(handlerMethod.getBeanType()).thenReturn((Class) Object.class);

        boolean result = jwtInterceptor.preHandle(request, response, handlerMethod);
        assertThat(result).isTrue();
        verify(jwtUtil, never()).parseToken("query.token");
    }

    private void mockClaims(String userId, String username, String roleCode, int version) {
        DefaultClaims claims = new DefaultClaims();
        claims.setSubject(userId);
        claims.put("username", username);
        claims.put("roleCode", roleCode);
        claims.put("version", version);
        when(jwtUtil.parseToken("valid.token")).thenReturn(claims);
    }

    private void mockValidToken(String userId, String username, String roleCode, int version) {
        when(request.getHeader("Authorization")).thenReturn("Bearer valid.token");
        DefaultClaims claims = new DefaultClaims();
        claims.setSubject(userId);
        claims.put("username", username);
        claims.put("roleCode", roleCode);
        claims.put("version", version);
        when(jwtUtil.parseToken("valid.token")).thenReturn(claims);
    }

    private RequireRole createRequireRole(String... values) {
        return new RequireRole() {
            @Override public Class<? extends java.lang.annotation.Annotation> annotationType() { return RequireRole.class; }
            @Override public String[] value() { return values; }
        };
    }
}
