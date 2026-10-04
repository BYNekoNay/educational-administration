package com.pzhu.eduadmin.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 登录失败计数与锁定（SEC-03）。
 *
 * <p>取代原先 AuthService 内的进程内 {@code ConcurrentHashMap}，解决两个问题：</p>
 * <ol>
 *   <li><b>多实例失效</b>：计数存在单个 JVM 堆里，多副本部署时可被攻击者轮流命中不同实例绕过；
 *       改为以 Redis 为主存储，跨实例共享。</li>
 *   <li><b>单维度可被滥用</b>：仅按用户名锁定会被反向利用为「锁定他人账号」的 DoS
 *       （连续用错误密码试某人的账号即可让本人 15 分钟无法登录）。这里拆成两个维度：
 *       账号维度（窄额度，精度高）与来源 IP 维度（宽额度，防止 NAT 出口误伤），
 *       任一超限即拒绝。</li>
 * </ol>
 *
 * <p><b>降级约定</b>：Redis 不可用（未部署、连接失败）时自动回落到进程内计数，
 * 防护强度退回单机级别但服务保持可用——安全组件不应成为可用性的单点。</p>
 *
 * <p>对应 docs/11-后端开发详细文档.md §2。</p>
 */
@Slf4j
@Component
public class LoginAttemptService {

    /** 同一账号连续失败上限 */
    private static final int MAX_FAILED_PER_USER = 5;
    /** 同一来源 IP 连续失败上限。刻意放宽：企业网/NAT 出口可能复用同一 IP */
    private static final int MAX_FAILED_PER_IP = 30;
    /** 锁定时长 */
    private static final long LOCKOUT_MS = 15 * 60 * 1000L;

    private static final String KEY_PREFIX = "auth:login-fail:";

    @Value("${app.security.login-lockout-seconds:900}")
    private long lockoutSeconds;

    private final StringRedisTemplate redis;

    /** Redis 不可用时的降级存储（仅保留近期失败，由 sweep() 控制内存上限） */
    private final ConcurrentHashMap<String, FallbackState> fallback = new ConcurrentHashMap<>();

    public LoginAttemptService(org.springframework.beans.factory.ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redis = redisProvider.getIfAvailable();
        if (this.redis == null) {
            log.warn("未检测到 StringRedisTemplate，登录失败计数降级为进程内实现（多副本部署下防护失效）");
        }
    }

    /**
     * 校验是否已被锁定。两个维度任一超限即拒绝。
     *
     * @param username 原始用户名（内部做 trim+lowercase 归一化，与 DB 大小写/尾空格不敏感排序规则对齐）
     * @param clientIp 来源 IP，可为 null（如定时任务等非 Web 上下文）
     */
    public void checkLocked(String username, String clientIp) {
        if (isLocked(userDimension(username))) {
            throw new com.pzhu.eduadmin.common.BusinessException(429, "账号已锁定，请 15 分钟后再试");
        }
        if (clientIp != null && !clientIp.isBlank() && isLocked(ipDimension(clientIp))) {
            throw new com.pzhu.eduadmin.common.BusinessException(429, "操作过于频繁，请稍后再试");
        }
    }

    /** 记录一次失败。达到阈值即触发锁定窗口。 */
    public void recordFailure(String username, String clientIp) {
        touch(userDimension(username));
        if (clientIp != null && !clientIp.isBlank()) {
            touch(ipDimension(clientIp));
        }
    }

    /** 登录成功后清除该账号的失败计数（成功一次即重置，避免累计误伤） */
    public void clearSuccess(String username, String clientIp) {
        clear(userDimension(username));
        if (clientIp != null && !clientIp.isBlank()) {
            clear(ipDimension(clientIp));
        }
    }

    // ====== 存储层：Redis 优先，失败降级 ======

    private boolean isLocked(String key) {
        try {
            if (redis != null) {
                String v = redis.opsForValue().get(key);
                return v != null && Integer.parseInt(v) >= thresholdOf(key);
            }
        } catch (RedisConnectionFailureException | NumberFormatException e) {
            log.warn("Redis 读取失败计数失败，降级为进程内判定: {}", e.getMessage());
        }
        FallbackState state = fallback.get(key);
        return state != null && state.value() >= thresholdOf(key);
    }

    private void touch(String key) {
        long ttlSeconds = lockoutSeconds > 0 ? lockoutSeconds : LOCKOUT_MS / 1000;
        try {
            if (redis != null) {
                Long count = redis.opsForValue().increment(key);
                if (count != null && count == 1) {
                    redis.expire(key, ttlSeconds, TimeUnit.SECONDS);
                }
                return;
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 记录失败计数失败，降级为进程内计数: {}", e.getMessage());
        }
        sweep();
        fallback.computeIfAbsent(key, k -> new FallbackState())
                .incrementAndGet(ttlSeconds * 1000);
    }

    private void clear(String key) {
        try {
            if (redis != null) {
                redis.delete(key);
            }
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis 清除失败计数失败，已忽略: {}", e.getMessage());
        }
        fallback.remove(key);
    }

    private int thresholdOf(String key) {
        return key.endsWith(":ip") ? MAX_FAILED_PER_IP : MAX_FAILED_PER_USER;
    }

    private String userDimension(String username) {
        String norm = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        return KEY_PREFIX + "user:" + norm;
    }

    private String ipDimension(String clientIp) {
        return KEY_PREFIX + "ip:" + clientIp.trim() + ":ip";
    }

    /** 清理过期的降级条目，防止内存随失败次数无界增长 */
    private void sweep() {
        long now = System.currentTimeMillis();
        fallback.entrySet().removeIf(e -> now > e.getValue().expireAt);
    }

    /** 降级存储的计数 + 过期时刻 */
    private static final class FallbackState {
        private volatile int value;
        private volatile long expireAt = System.currentTimeMillis() + LOCKOUT_MS;

        synchronized int incrementAndGet(long ttlMillis) {
            value++;
            expireAt = System.currentTimeMillis() + ttlMillis;
            return value;
        }

        int value() {
            if (System.currentTimeMillis() > expireAt) {
                return 0;
            }
            return value;
        }
    }
}
