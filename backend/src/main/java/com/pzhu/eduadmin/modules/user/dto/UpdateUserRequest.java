package com.pzhu.eduadmin.modules.user.dto;

import lombok.Data;

import java.util.List;

@Data
public class UpdateUserRequest {

    private String username;

    private String realName;

    private String phone;

    private String roleCode;

    /** 教学特长课程ID列表（仅当 roleCode 为 TEACHER 时有效） */
    private List<Long> specialtyCourseIds;
}
