package com.pzhu.eduadmin.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 导出直链下载一次性票据。
 *
 * <p>与 {@link StreamTicketService} 同一模式（60 秒、取出即删），但绑定端点：
 * 票据签发时锁定目标导出 URI，消费时校验 URI 一致——票据不可跨端点挪用，
 * 拿 A 端点票据请求 B 端点会被拒。</p>
 *
 * <p>背景：前端导出原本走 fetch+blob，本机浏览器对 Office 文件的下载安全扫描
 * 会让下载停在 .crdownload；改直链（&lt;a href&gt;）后浏览器走原生下载通道，
 * 但 &lt;a&gt; 无法带 Authorization 头，因此先用 Bearer 鉴权换一次性票据。</p>
 */
@Service
public class DownloadTicketService {

    private static final long TTL_MILLIS = 60_000L;

    private record Entry(Long userId, String endpoint, long expireAt) {}

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    /** 签发绑定端点的一次性票据 */
    public String issue(Long userId, String endpoint) {
        cleanup();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(userId, endpoint, System.currentTimeMillis() + TTL_MILLIS));
        return ticket;
    }

    /**
     * 消费票据：取出即删；不存在 / 过期 / 端点不匹配返回 null。
     *
     * @param ticket   签发时返回的票据
     * @param endpoint 请求目标 URI（须与签发时一致）
     */
    public Long consume(String ticket, String endpoint) {
        if (ticket == null || ticket.isBlank() || endpoint == null) {
            return null;
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expireAt() < System.currentTimeMillis()
                || !entry.endpoint().equals(endpoint)) {
            return null;
        }
        return entry.userId();
    }

    private void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }
}
