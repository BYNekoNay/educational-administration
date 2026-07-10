package com.pzhu.eduadmin.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标注接口允许访问的角色代码，配合 JwtInterceptor 做角色校验。
 * 未标注该注解的接口默认只要求登录（Token 有效）即可访问。
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    String[] value();
}
