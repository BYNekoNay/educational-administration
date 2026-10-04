package com.pzhu.eduadmin.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 签发与解析工具类。对应 docs/11-后端开发详细文档.md §1.3。
 */
@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** 允许的算法白名单（SEC-05）：本项目只签发 HMAC-SHA256，解析时也只认 HS256。 */
    private static final String ALLOWED_ALG = SignatureAlgorithm.HS256.getValue();

    private static final ObjectMapper HEADER_MAPPER = new ObjectMapper();

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-minutes}")
    private long expireMinutes;

    private SecretKey key() {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            // 短密钥零填充降低安全强度，生产环境必须配置 >= 32 字节的 jwt.secret
            log.error("JWT secret is shorter than the required 32 UTF-8 bytes (current: {})", bytes.length);
            throw new IllegalStateException("JWT secret must be at least 32 UTF-8 bytes");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String generateToken(Long userId, String username, String roleCode, Integer version) {
        Date now = new Date();
        Date expireAt = new Date(now.getTime() + expireMinutes * 60 * 1000);
        return Jwts.builder()
                .setSubject(String.valueOf(userId))
                .claim("username", username)
                .claim("roleCode", roleCode)
                .claim("version", version != null ? version : 0)
                .setIssuedAt(now)
                .setExpiration(expireAt)
                .signWith(key(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseToken(String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token 为空");
        }
        // 算法白名单前置校验（SEC-05）。必须在验签之前显式拒绝非 HS256（含 alg=none
        // 与 RS256 等算法混淆），而不是把判定留给解析库的隐式行为——后者一旦随版本
        // 变更而放宽，将直接沦为签名绕过且毫无告警。
        String alg = readAlgorithm(token);
        if (!ALLOWED_ALG.equals(alg)) {
            log.warn("Rejected JWT with unexpected alg={}", alg);
            throw new IllegalArgumentException("Token 算法不合法，仅支持 " + ALLOWED_ALG);
        }
        return Jwts.parserBuilder()
                .setSigningKey(key())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 读取 JOSE Header 的 alg 字段，只读不解签。
     * 解析失败（非 JWT 结构 / 非法 Base64 / 非法 JSON）统一返回 null，交由调用方按
     * 白名单拒绝；此处不抛 JSON 异常，避免把解析库错误类型渗给上层造成语义混淆。
     */
    private String readAlgorithm(String token) {
        try {
            int firstDot = token.indexOf('.');
            if (firstDot <= 0) {
                return null;
            }
            String headerJson = new String(Base64.getUrlDecoder().decode(token.substring(0, firstDot)),
                    StandardCharsets.UTF_8);
            JsonNode node = HEADER_MAPPER.readTree(headerJson);
            JsonNode algNode = node.get("alg");
            return algNode == null ? null : algNode.asText();
        } catch (IllegalArgumentException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return null;
        }
    }
}
