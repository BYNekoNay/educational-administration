package com.pzhu.eduadmin.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * IP 工具类：从 HTTP 请求中提取客户端真实 IP 地址。
 * 支持 X-Forwarded-For、X-Real-IP 等代理头。
 */
public final class IpUtil {

    private IpUtil() {}

    /**
     * 从 Spring 上下文获取当前请求的客户端 IP。
     * 若无请求上下文（如定时任务），返回 "0.0.0.0"。
     */
    public static String getCurrentIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return "0.0.0.0";
        return getClientIp(attrs.getRequest());
    }

    /**
     * 获取客户端真实 IP 地址。
     * 优先级：X-Forwarded-For → X-Real-IP → Proxy-Client-IP → remoteAddr
     */
    public static String getClientIp(HttpServletRequest request) {
        if (request == null) return "0.0.0.0";
        // M10 fix: 代理头完全可被客户端伪造，必须做格式/长度清洗，
        // 否则超长 XFF 会撑爆 operation_log.ip(VARCHAR(50))，导致业务事务回滚
        String ip = sanitizeIp(request.getHeader("X-Forwarded-For"));
        if (ip != null) return ip;
        ip = sanitizeIp(request.getHeader("X-Real-IP"));
        if (ip != null) return ip;
        ip = sanitizeIp(request.getHeader("Proxy-Client-IP"));
        if (ip != null) return ip;
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0";
    }

    private static final java.util.regex.Pattern IPV4 =
            java.util.regex.Pattern.compile("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$");
    private static final java.util.regex.Pattern IPV6 =
            java.util.regex.Pattern.compile("^[0-9A-Fa-f:]+$");

    /**
     * 清洗候选 IP：取首个逗号分段，去空白，拒绝 blank/unknown，限制长度并校验 IPv4/IPv6 格式。
     * 不合法返回 null，由调用方尝试下一个来源。
     */
    private static String sanitizeIp(String raw) {
        if (raw == null) return null;
        String candidate = raw.trim();
        int commaIdx = candidate.indexOf(',');
        if (commaIdx > 0) candidate = candidate.substring(0, commaIdx).trim();
        if (candidate.isEmpty() || "unknown".equalsIgnoreCase(candidate)) return null;
        if (candidate.length() > 45) return null; // IPv6 最长 45 字符，超长直接拒绝
        java.util.regex.Matcher m = IPV4.matcher(candidate);
        if (m.matches()) {
            for (int i = 1; i <= 4; i++) {
                if (Integer.parseInt(m.group(i)) > 255) return null;
            }
            return candidate;
        }
        if (candidate.contains(":") && IPV6.matcher(candidate).matches()) {
            return candidate;
        }
        return null;
    }
}
