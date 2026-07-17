package com.pzhu.eduadmin;

import com.pzhu.eduadmin.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 集成测试基类 — 使用真实后端实例 + 真实 MySQL 数据库。
 * 需要 MySQL 运行且 edu_admin 库已建表（sql/schema.sql）。
 * 若不满足条件，请使用 ServiceMockTest（纯 Mockito，无需数据库）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseSpringTest {

    @Autowired
    protected JwtUtil jwtUtil;

    protected String buildToken(Long userId, String username, String roleCode) {
        return "Bearer " + jwtUtil.generateToken(userId, username, roleCode, 0);
    }

    protected String adminToken() { return buildToken(1L, "admin", "SUPER_ADMIN"); }
    protected String teacherToken() { return buildToken(2L, "teacher1", "TEACHER"); }
    protected String financeToken() { return buildToken(4L, "finance", "FINANCE"); }
    protected String parentToken() { return buildToken(6L, "parent1", "PARENT"); }
    protected String expiredToken() { return "Bearer eyJhbGciOiJIUzI1NiJ9.eyJ1c2VySWQiOjF9.xxx"; }
}
