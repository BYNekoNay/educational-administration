package com.pzhu.eduadmin.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
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
 *
 * <p><b>为什么 {@code proxyTargetClass = true}：</b>
 * {@code @EnableCaching} 默认 {@code proxyTargetClass = false}，即优先用 JDK 动态代理。
 * 而本项目的 {@code @Cacheable} / {@code @CacheEvict} 全部标注在<b>实现类</b>方法上、
 * 接口方法上没有同注解 —— JDK 代理只能看到接口，于是缓存注解被完全忽略，
 * 表现为「CacheManager 已是 RedisCacheManager，但 Redis 里没有任何 key，@Cacheable 也不缓存」。</p>
 *
 * <p><b>⚠️ 已知未解决问题（2026-11-25 实测）：</b>
 * 即使补齐 starter-cache、提供 cacheManager()、开启 proxyTargetClass，
 * <b>生产环境（cache.type=redis）下 {@code CacheAspectSupport} 仍无任何执行痕迹</b>：
 * Redis MONITOR 只见 health 的 PING（无 GET/SET/EVAL），{@code DBSIZE} 恒为 0，
 * 而 {@code AnnotationCacheOperationSource} 的 TRACE 日志证明注解已被正确解析。
 * 同一份代码在测试环境（cache.type=simple）下 {@link com.pzhu.eduadmin.CacheInterceptionProbeTest}
 * 可以通过，说明拦截逻辑本身没问题，差异出在 redis 缓存管理器这条装配链路上，根因待查。
 * 因此生产暂未启用本类改动后的镜像，缓存实际仍落 JVM 内存（单实例下功能等价）。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class CacheConfig implements CachingConfigurer {

    /**
     * 用 ObjectProvider 延迟解析：CacheManager 由 Boot 的 CacheAutoConfiguration 按
     * {@code spring.cache.type} 创建，与本配置类存在装配先后关系，直接注入可能构成循环依赖。
     */
    private final ObjectProvider<CacheManager> cacheManagerProvider;

    /**
     * 必须显式提供：{@link CachingConfigurer#cacheManager()} 的默认实现返回 null，
     * 一旦本类实现了 CachingConfigurer 却不覆盖它，CacheInterceptor 就拿不到容器里的
     * CacheManager（Boot 按 {@code spring.cache.type=redis} 创建的 RedisCacheManager），
     * 从而回落到默认的内存实现 —— 表现为「配置了 Redis，但 Redis 里没有任何缓存 key」。
     */
    @Override
    public CacheManager cacheManager() {
        CacheManager resolved = cacheManagerProvider.getIfAvailable();
        if (resolved == null) {
            log.warn("未解析到 CacheManager，缓存将回落默认实现（若期望 Redis 缓存，请检查 spring.cache.type 与 starter-cache 依赖）");
        } else {
            log.info("缓存管理器已装配: {}", resolved.getClass().getName());
        }
        return resolved;
    }

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
