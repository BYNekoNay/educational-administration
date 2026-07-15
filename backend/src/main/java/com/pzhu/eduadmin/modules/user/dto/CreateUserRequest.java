package com.pzhu.eduadmin.modules.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateUserRequest {

    @NotBlank(message = "用户名不能为空")
    private String username;

    @NotBlank(message = "密码不能为空")
    private String password;

    @NotBlank(message = "姓名不能为空")
    private String realName;

    private String phone;

    @NotBlank(message = "角色不能为空")
    private String roleCode;

    /** 教学特长课程ID列表（仅当 roleCode=TEACHER 时有效） */
    private List<Long> specialtyCourseIds;
}
