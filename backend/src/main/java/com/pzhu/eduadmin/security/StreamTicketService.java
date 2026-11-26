package com.pzhu.eduadmin.security;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSE 一次性票据。
 *
 * <p>EventSource 不支持自定义 Header，凭证只能放进 URL。原先直接把 JWT 拼到查询串，
 * 会落进 nginx 日志、浏览器历史、Referer，且长有效期 JWT 一旦泄露即长期有效。
 *
 * <p>改为：先用正常 Bearer 鉴权调用 {@code POST /api/auth/stream-ticket} 换一个
 * 60 秒、单次使用的 ticket，再用它建立 SSE 连接。ticket 取出即删，重放无效。
 */
@Service
public class StreamTicketService {

    private static final long TTL_MILLIS = 60_000L;

    private record Entry(Long userId, long expireAt) {}

    private final Map<String, Entry> tickets = new ConcurrentHashMap<>();

    /** 签发一次性票据 */
    public String issue(Long userId) {
        cleanup();
        String ticket = UUID.randomUUID().toString().replace("-", "");
        tickets.put(ticket, new Entry(userId, System.currentTimeMillis() + TTL_MILLIS));
        return ticket;
    }

    /** 消费票据：取出即删；不存在或已过期返回 null */
    public Long consume(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        Entry entry = tickets.remove(ticket);
        if (entry == null || entry.expireAt() < System.currentTimeMillis()) {
            return null;
        }
        return entry.userId();
    }

    /** 顺带清理过期票据，避免长期运行后 Map 膨胀 */
    private void cleanup() {
        long now = System.currentTimeMillis();
        tickets.entrySet().removeIf(e -> e.getValue().expireAt() < now);
    }
}
