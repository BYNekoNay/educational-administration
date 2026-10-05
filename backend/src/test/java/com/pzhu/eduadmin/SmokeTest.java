package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 集成冒烟测试 — 使用 H2 内存数据库验证 Spring 上下文可正常加载。
 * 需要 src/test/resources/application-test.yml 配置文件。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@DisplayName("集成冒烟测试")
class SmokeTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("Spring 上下文加载成功")
    void contextLoads() {
        // 断言真实上下文证据，而不是 assertThat(true) 这种永真占位：
        // 容器必须真的装好 Bean，且安全/持久层核心 Bean 可取到。
        assertThat(applicationContext).isNotNull();
        assertThat(applicationContext.getBeanDefinitionCount()).isPositive();
        assertThat(applicationContext.getBean(JwtUtil.class)).isNotNull();
        assertThat(applicationContext.getBean(UserMapper.class)).isNotNull();
    }

    @Test
    @DisplayName("测试 Profile 为 test")
    void activeProfileIsTest() {
        // 原写法 System.getProperty("spring.profiles.active", "test") 在属性缺失时默认取 "test"，
        // 恒等于期望值，属占位断言；改为断言 Environment 中真实激活的 profile。
        assertThat(environment.getActiveProfiles()).containsExactly("test");
    }
}
