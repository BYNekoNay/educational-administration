package com.pzhu.eduadmin.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Configuration;

/**
 * 缓存基础设施配置（P-05）。
 *
 * <p>仅负责开启缓存抽象并注册"容错错误处理器"，缓存类型/TTL 通过配置声明
 * （{@code spring.cache.type=redis} + {@code spring.cache.redis.time-to-live}，见 application.yml）。
 * 测试环境（application-test.yml）排除 Redis 自动配置并改用 {@code simple} 内存缓存，
 * 保证无 Redis 时 484 个用例仍全绿。</p>
 *
 * <p>容错处理是关键约束：缓存是"可丢失的加速层"，Redis 不可用时读缓存失败必须回落到真实查询、
 * 写缓存/失效失败只记日志，绝不让缓存异常改变业务返回值或使写操作失败。</p>
 */
@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存读取失败，降级为直接查询: cache={}, key={}, err={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("缓存写入失败，已忽略: cache={}, key={}, err={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("缓存失效失败，已忽略: cache={}, key={}, err={}",
                        cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("缓存清空失败，已忽略: cache={}, err={}",
                        cache.getName(), exception.getMessage());
            }
        };
    }
}
