package com.pzhu.eduadmin.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50 位")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需为 6-32 位")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(max = 50, message = "姓名长度不能超过 50 位")
    private String realName;

    @Size(max = 20, message = "手机号长度不能超过 20 位")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "角色不能为空")
    @Size(max = 30, message = "角色编码长度不能超过 30 位")
    private String roleCode;

    /** 教学特长课程ID列表（仅当 roleCode=TEACHER 时有效） */
    private List<Long> specialtyCourseIds;
}
