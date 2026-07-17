package com.pzhu.eduadmin.modules.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {

    @Size(min = 3, max = 32, message = "用户名长度需为 3-32 位")
    private String username;

    @Size(max = 20, message = "姓名最长 20 位")
    private String realName;

    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    private String roleCode;

    /** 教学特长课程ID列表（仅当 roleCode 为 TEACHER 时有效） */
    private List<Long> specialtyCourseIds;
}
