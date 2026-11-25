package com.pzhu.eduadmin.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 轻量限流服务：给低频敏感接口（如「单条查看家长联系方式」）压压力。
 * <p>定位说明：这<b>不是安全边界</b>，真正的权限边界是任教关系判定；
 * 限流只是避免有人用单条接口把全机构家长电话一条条刷出来。
 * <p>Redis 不可用时降级为进程内计数，不阻断业务——与 {@code LoginAttemptService}
 * 的降级口径一致（缓存是可丢失的加速层）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    /** 默认每分钟 60 次 */
    private static final int DEFAULT_LIMIT = 60;
    private static final Duration WINDOW = Duration.ofMinutes(1);

    /**
     * 用 ObjectProvider 而非直接注入：本项目的 Redis 是可选依赖，
     * 直接构造注入会在未配置 Redis 的环境启动失败。
     */
    private final ObjectProvider<StringRedisTemplate> redisTemplateProvider;

    /** 降级计数：key -> [窗口起始毫秒, 计数] */
    private final Map<String, long[]> localCounters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String key) {
        return tryAcquire(key, DEFAULT_LIMIT);
    }

    public boolean tryAcquire(String key, int limit) {
        StringRedisTemplate redis = redisTemplateProvider.getIfAvailable();
        if (redis == null) {
            return tryAcquireLocal(key, limit);
        }
        try {
            String redisKey = "ratelimit:" + key;
            Long current = redis.opsForValue().increment(redisKey);
            if (current != null && current == 1L) {
                redis.expire(redisKey, WINDOW);
            }
            return current == null || current <= limit;
        } catch (Exception e) {
            log.debug("限流 Redis 不可用，降级为进程内计数: {}", e.getMessage());
            return tryAcquireLocal(key, limit);
        }
    }

    private boolean tryAcquireLocal(String key, int limit) {
        long now = System.currentTimeMillis();
        long[] slot = localCounters.computeIfAbsent(key, k -> new long[]{now, 0});
        synchronized (slot) {
            if (now - slot[0] > WINDOW.toMillis()) {
                slot[0] = now;
                slot[1] = 0;
            }
            slot[1]++;
            return slot[1] <= limit;
        }
    }
}
