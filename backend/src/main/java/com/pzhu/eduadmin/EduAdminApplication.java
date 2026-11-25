package com.pzhu.eduadmin;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// 放在主类而非 CacheConfig：确保缓存 advisor 在任何业务 bean 被代理之前注册。
// proxyTargetClass=true：@Cacheable/@CacheEvict 标注在实现类方法上，JDK 动态代理看不到。
@EnableCaching(proxyTargetClass = true)
public class EduAdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(EduAdminApplication.class, args);
    }
}
