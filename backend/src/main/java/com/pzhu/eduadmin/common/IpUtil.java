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
        String ip = request.getHeader("X-Forwarded-For");
        if (isValidIp(ip)) {
            // X-Forwarded-For 可能包含多个 IP，取第一个
            int commaIdx = ip.indexOf(',');
            return commaIdx > 0 ? ip.substring(0, commaIdx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isValidIp(ip)) return ip.trim();
        ip = request.getHeader("Proxy-Client-IP");
        if (isValidIp(ip)) return ip.trim();
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "0.0.0.0";
    }

    private static boolean isValidIp(String ip) {
        return ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip);
    }
}
