package com.pzhu.eduadmin.security;

/**
 * 基于 ThreadLocal 保存当前请求的登录用户，供 Service 层做资源归属二次校验
 * （对应 docs/11-后端开发详细文档.md §1.3、docs/05-详细设计说明书.md §8 数据行级隔离）。
 */
public class CurrentUserHolder {

    private static final ThreadLocal<LoginUser> HOLDER = new ThreadLocal<>();

    private CurrentUserHolder() {
    }

    public static void set(LoginUser loginUser) {
        HOLDER.set(loginUser);
    }

    public static LoginUser get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
