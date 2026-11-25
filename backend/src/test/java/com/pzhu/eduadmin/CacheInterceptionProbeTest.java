package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.risk.service.RiskWarningService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 缓存拦截探针：确认 {@code @Cacheable} 真的被 AOP 拦截并写入缓存。
 *
 * <p>背景：生产环境曾出现「CacheManager 已是 RedisCacheManager，但 Redis 里没有任何 key」
 * 的现象。该测试用 simple 内存缓存断言拦截是否发生，与 Redis 无关，
 * 因此能区分「拦截没生效」和「拦截生效但 Redis 配置有问题」。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("缓存拦截探针")
class CacheInterceptionProbeTest {

    @Autowired
    private RiskWarningService riskWarningService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("RiskWarningService 应为 AOP 代理")
    void serviceShouldBeProxied() {
        assertThat(AopUtils.isAopProxy(riskWarningService))
                .as("RiskWarningService 未被代理，@Cacheable 必然失效")
                .isTrue();
        System.out.println("[探针] 代理类型 = " + riskWarningService.getClass().getName());
        System.out.println("[探针] 目标类型 = " + AopUtils.getTargetClass(riskWarningService).getName());
        System.out.println("[探针] CacheManager = " + cacheManager.getClass().getName());
    }

    @Test
    @DisplayName("调用 @Cacheable 的 summary() 后，缓存区应有条目")
    void summaryShouldBePopulatedInCache() {
        Cache cache = cacheManager.getCache("riskSummary");
        assertThat(cache).as("CacheManager 应存在 riskSummary 缓存区").isNotNull();
        cache.clear();

        riskWarningService.summary();

        Cache.ValueWrapper hit = cache.get(SimpleKey.EMPTY);
        assertThat(hit)
                .as("@Cacheable 未生效：无参方法调用后 riskSummary 缓存区仍为空")
                .isNotNull();
    }
}
