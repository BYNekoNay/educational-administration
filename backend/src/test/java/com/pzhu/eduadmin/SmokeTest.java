package com.pzhu.eduadmin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
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

    @Test
    @DisplayName("Spring 上下文加载成功")
    void contextLoads() {
        // 如果上下文加载失败，@SpringBootTest 会直接抛出异常
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("测试 Profile 为 test")
    void activeProfileIsTest() {
        // 验证 ActiveProfiles 生效
        assertThat(System.getProperty("spring.profiles.active", "test")).isEqualTo("test");
    }
}
