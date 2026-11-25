package com.pzhu.eduadmin.integration;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.student.dto.ParentSelfBindRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 家长自助绑定学员的集成测试：验证「姓名 + 联系电话」双要素匹配、
 * 软删学员排除、重复绑定拦截在真实 SQL 下的行为。
 */
@DisplayName("家长自助绑定学员 - 集成测试")
class ParentSelfBindIntegrationTest extends IntegrationTestSupport {

    private ParentSelfBindRequest request(String name, String phone, String relation) {
        ParentSelfBindRequest r = new ParentSelfBindRequest();
        r.setStudentName(name);
        r.setPhone(phone);
        r.setRelation(relation);
        return r;
    }

    @Test
    @DisplayName("姓名+电话唯一命中 - 建立绑定关系并落库")
    void shouldBindOnUniqueMatch() {
        loginAs("PARENT");
        Long parentId = givenParentUser("parent-it-1");
        Long studentId = givenStudent("张小雨", "13800138000");

        studentService.bindParentSelf(parentId, request("张小雨", "13800138000", "母亲"));

        assertThat(bindingsOf(studentId)).hasSize(1);
        assertThat(bindingsOf(studentId).get(0).getParentUserId()).isEqualTo(parentId);
        assertThat(bindingsOf(studentId).get(0).getRelation()).isEqualTo("母亲");
    }

    @Test
    @DisplayName("同名同电话两条 - 抛 409，要求联系教务人工绑定")
    void shouldRejectMultipleMatches() {
        loginAs("PARENT");
        Long parentId = givenParentUser("parent-it-2");
        givenStudent("张小雨", "13800138000");
        givenStudent("张小雨", "13800138000");

        assertThatThrownBy(() -> studentService.bindParentSelf(parentId, request("张小雨", "13800138000", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("匹配到多名学员");
    }

    @Test
    @DisplayName("学员已被软删 - 抛 404（@TableLogic 自动过滤）")
    void shouldRejectSoftDeletedStudent() {
        loginAs("PARENT");
        Long parentId = givenParentUser("parent-it-3");
        Long studentId = givenStudent("李四", "13900139000");
        jdbcTemplate.update("UPDATE student SET is_deleted = 1 WHERE id = ?", studentId);

        assertThatThrownBy(() -> studentService.bindParentSelf(parentId, request("李四", "13900139000", null)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到匹配的学员");
    }

    @Test
    @DisplayName("重复绑定同一学员 - 抛 409，绑定关系仍为 1 条")
    void shouldRejectDuplicateBinding() {
        loginAs("PARENT");
        Long parentId = givenParentUser("parent-it-4");
        Long studentId = givenStudent("王五", "13700137000");

        studentService.bindParentSelf(parentId, request("王五", "13700137000", "父亲"));

        assertThatThrownBy(() -> studentService.bindParentSelf(parentId, request("王五", "13700137000", "父亲")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请勿重复绑定");

        assertThat(bindingsOf(studentId)).hasSize(1);
    }
}
