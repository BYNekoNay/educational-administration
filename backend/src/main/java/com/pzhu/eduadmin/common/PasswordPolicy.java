package com.pzhu.eduadmin.common;

import com.pzhu.eduadmin.common.BusinessException;

import java.nio.charset.StandardCharsets;

/**
 * 统一密码强度策略。
 *
 * <p>调用方：管理端重置（UserServiceImpl.resetPassword）与个人改密
 * （AuthService.changePassword）。原本两处各维护一份逐行重复的校验，
 * 第六轮新增「字母+数字组合」后收敛到此类，避免规则分叉。</p>
 *
 * <p>兼容性说明：初始密码（data.sql 的 123456 等纯数字密码）由 SQL 直接落库、
 * 不经本校验，登录不受影响；凡走改密/重置接口新设的密码必须含字母和数字。</p>
 */
public final class PasswordPolicy {

    private PasswordPolicy() {
    }

    /** 非空 / ≥6位 / ≤72字节 / 必须同时含字母和数字。消息文案与既有测试断言一致，勿改措辞。 */
    public static void validate(String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(400, "新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于6位");
        }
        // BCrypt 有效上限 72 字节，超长密码会导致 CPU 密集型哈希（DoS 风险），
        // 且超长部分被静默截断——前 72 字节相同的两个密码哈希一致。
        if (newPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(400, "新密码过长（超出72字节限制）");
        }
        if (!newPassword.matches(".*[a-zA-Z].*") || !newPassword.matches(".*[0-9].*")) {
            throw new BusinessException(400, "新密码需同时包含字母和数字");
        }
    }
}
