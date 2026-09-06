package com.pzhu.eduadmin;

import com.pzhu.eduadmin.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JWT 工具类单元测试")
class JwtUtilTest {

    private final JwtUtil jwtUtil = new JwtUtil();

    @BeforeEach
    void setUp() throws Exception {
        setField("secret", "test-secret-key-must-be-at-least-32-bytes-long!!");
        setField("expireMinutes", 60L);
    }

    private void setField(String name, Object value) throws Exception {
        Field f = JwtUtil.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(jwtUtil, value);
    }

    @Test
    @DisplayName("正常签发和解析 Token")
    void generateAndParse() {
        String token = jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", 0);
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo("1");
        assertThat(claims.get("username", String.class)).isEqualTo("admin");
        assertThat(claims.get("roleCode", String.class)).isEqualTo("SUPER_ADMIN");
        assertThat(claims.get("version", Integer.class)).isEqualTo(0);
    }

    @Test
    @DisplayName("Token 过期 — 抛出 ExpiredJwtException")
    void parseToken_Expired() throws Exception {
        setField("expireMinutes", -1L); // 立即过期
        String token = jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", 0);

        assertThatThrownBy(() -> jwtUtil.parseToken(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("篡改 Token — 解析失败")
    void parseToken_Tampered() {
        String token = jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", 0);
        String tampered = token.substring(0, token.length() - 4) + "XXXX";

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("version 为 null 默认使用 0")
    void generateToken_VersionNull() {
        String token = jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", null);
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.get("version", Integer.class)).isEqualTo(0);
    }

    @Test
    @DisplayName("用户名包含特殊字符")
    void generateToken_SpecialChars() {
        String token = jwtUtil.generateToken(1L, "user@name.com", "PARENT", 1);
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.get("username", String.class)).isEqualTo("user@name.com");
    }

    @Test
    @DisplayName("短密钥自动填充到 32 字节（key() 方法）")
    void key_ShortSecretIsRejected() throws Exception {
        setField("secret", "short");
        // key() 是 private，通过 generate+parse 间接验证
        assertThatThrownBy(() -> jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least 32 UTF-8 bytes");
    }
}
