package com.pzhu.eduadmin;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.security.LoginAttemptService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 登录失败计数服务单元测试（SEC-03）。
 *
 * <p>覆盖三条路径：Redis 正常（分布式共享）、Redis 连接失败（降级为进程内）、
 * 以及"账号维度"与"IP 维度"两个独立阈值的判定。</p>
 */
@DisplayName("登录失败计数服务单元测试")
class LoginAttemptServiceTest {

    private final Map<String, Long> redisStore = new HashMap<>();

    private StringRedisTemplate redis;
    private LoginAttemptService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisStore.clear();
        redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        // 用内存 Map 模拟 Redis 的 INCR 语义
        when(redis.opsForValue()).thenReturn(ops);
        when(ops.increment(any())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            return redisStore.merge(key, 1L, Long::sum);
        });
        when(ops.get(any())).thenAnswer(inv -> {
            Long v = redisStore.get(inv.<String>getArgument(0));
            return v == null ? null : String.valueOf(v);
        });
        doAnswer(inv -> redisStore.remove(inv.<String>getArgument(0)) != null)
                .when(redis).delete(anyString());

        service = new LoginAttemptService(providerOf(redis));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<StringRedisTemplate> providerOf(StringRedisTemplate value) {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    @Test
    @DisplayName("账号维度：连续5次失败后拒绝，在此之前放行")
    void accountDimension_LocksAfterThreshold() {
        for (int i = 0; i < 4; i++) {
            service.recordFailure("admin", null);
        }
        service.checkLocked("admin", null); // 第4次：尚未锁定

        service.recordFailure("admin", null); // 第5次达到阈值
        assertThatThrownBy(() -> service.checkLocked("admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已锁定");
    }

    @Test
    @DisplayName("IP 维度独立生效：同一账号不同来源不会互相抬升，但同 IP 累计超限会被拒")
    void ipDimension_IsIndependent() {
        String ip = "203.0.113.7";
        // 换 29 个不同账号在同一 IP 上失败，单个账号均未达 5 次阈值
        for (int i = 0; i < 29; i++) {
            service.recordFailure("u" + i, ip);
            service.checkLocked("u" + i, ip);
        }
        service.recordFailure("u-last", ip); // 第 30 次：IP 维度触顶
        assertThatThrownBy(() -> service.checkLocked("u-last", ip))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("操作过于频繁");
    }

    @Test
    @DisplayName("用户名归一化：大小写与尾空格共享同一计数，无法借变体扩大破解额度")
    void username_IsNormalized() {
        service.recordFailure("Admin", null);
        service.recordFailure("ADMIN ", null);
        service.recordFailure(" admin", null);
        service.recordFailure("admin", null);
        service.recordFailure("admin  ", null);

        assertThat(redisStore.keySet()).hasSize(1);
        assertThatThrownBy(() -> service.checkLocked("aDmIn", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已锁定");
    }

    @Test
    @DisplayName("登录成功清除该维度的计数")
    void clearSuccess_ResetsCounter() {
        for (int i = 0; i < 5; i++) {
            service.recordFailure("admin", "203.0.113.7");
        }
        service.clearSuccess("admin", "203.0.113.7");

        service.checkLocked("admin", "203.0.113.7"); // 不应再抛
        verify(redis, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("Redis 不可用：自动降级为进程内计数，防护仍生效且不阻断服务")
    void redisUnavailable_FallsBackToInMemory() {
        StringRedisTemplate broken = mock(StringRedisTemplate.class);
        when(broken.opsForValue()).thenThrow(
                new RedisConnectionFailureException("Connection refused"));
        LoginAttemptService degraded = new LoginAttemptService(providerOf(broken));

        for (int i = 0; i < 5; i++) {
            degraded.recordFailure("admin", null);
        }
        assertThatThrownBy(() -> degraded.checkLocked("admin", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已锁定");
    }

    @Test
    @DisplayName("Spring 上下文未提供 StringRedisTemplate 时不抛异常（構造期降级）")
    void missingRedisBean_DoesNotFailConstruction() {
        @SuppressWarnings("unchecked")
        ObjectProvider<StringRedisTemplate> empty = mock(ObjectProvider.class);
        when(empty.getIfAvailable()).thenReturn(null);

        LoginAttemptService noRedis = new LoginAttemptService(empty);
        noRedis.checkLocked("admin", "203.0.113.7"); // 放行
        noRedis.recordFailure("admin", null);
        noRedis.clearSuccess("admin", null);
    }

    @Test
    @DisplayName("首次计数时设置过期 TTL，避免锁定键永久驻留 Redis")
    void firstIncrement_SetsTtl() {
        service.recordFailure("admin", null);
        verify(redis, times(1)).expire(any(), anyLong(), any());
    }

    @Test
    @DisplayName("非首次计数不重复刷新 TTL（不延长已锁定时间窗口）")
    void laterIncrement_DoesNotRefreshTtl() {
        service.recordFailure("admin", null);
        service.recordFailure("admin", null);
        // 只有 count==1 那一次会设置 expire；第二次不再刷新，避免持续错误登录无限延长锁定窗口
        verify(redis, times(1)).expire(any(), anyLong(), any());
        assertThat(redisStore).containsEntry("auth:login-fail:user:admin", 2L);
    }

    @Test
    @DisplayName("IP 为空时不进行 IP 维度判定（防 nulled key 合并计数）")
    void blankIp_IsIgnored() {
        service.recordFailure("admin", "");
        service.recordFailure("admin", "   ");
        verify(redis, never()).delete(anyString());
        assertThat(redisStore.keySet()).hasSize(1);
    }

    @Test
    @DisplayName("正常路径下确实用 Redis key 记录（分布式共享前提）")
    void usesRedisKeySpace() {
        service.recordFailure("admin", "203.0.113.7");
        assertThat(redisStore).containsKey("auth:login-fail:user:admin");
        assertThat(redisStore).containsKey("auth:login-fail:ip:203.0.113.7:ip");
    }

    @Test
    @DisplayName("账号维度与 IP 维度阈值不同：账号 5 次、IP 30 次")
    void thresholds_DifferPerDimension() {
        // IP 维度计数到 29 次仍不锁定（阈值 30）
        for (int i = 0; i < 29; i++) {
            service.recordFailure("user" + i, "198.51.100.1");
        }
        service.checkLocked("never-used", "198.51.100.1");
        // 账号维度 5 次即锁定
        for (int i = 0; i < 5; i++) {
            service.recordFailure("other", "198.51.100.2");
        }
        assertThatThrownBy(() -> service.checkLocked("other", "198.51.100.2"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已锁定");
    }

    @Test
    @DisplayName("多个失败维度互不干扰：A IP 超限不影响 B IP")
    void ipIsolation_AcrossSources() {
        String ipA = "192.0.2.1";
        String ipB = "192.0.2.2";
        for (int i = 0; i < 30; i++) {
            service.recordFailure("u" + i, ipA);
        }
        assertThatThrownBy(() -> service.checkLocked("u-x", ipA))
                .isInstanceOf(BusinessException.class);
        service.checkLocked("u-x", ipB); // 另一来源放行
    }

    @Test
    @DisplayName("计数键使用项目前缀，避免与业务 Redis key 冲突")
    void keyPrefix_IsNamespaced() {
        service.recordFailure("admin", "203.0.113.7");
        assertThat(redisStore.keySet()).allSatisfy(
                k -> assertThat(k).startsWith("auth:login-fail:"));
        verify(redis, atLeastOnce()).opsForValue();
        verify(redis, atLeastOnce()).expire(eq("auth:login-fail:user:admin"), anyLong(), any());
    }
}
