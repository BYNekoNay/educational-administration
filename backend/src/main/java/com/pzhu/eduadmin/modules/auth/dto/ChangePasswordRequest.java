package com.pzhu.eduadmin.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 本人修改密码请求。
 * <p>只允许修改自己的密码：userId 一律取自 CurrentUserHolder，
 * 不接受前端传入，避免"改他人密码"的越权路径。
 */
@Data
public class ChangePasswordRequest {

    @NotBlank(message = "原密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;
}
