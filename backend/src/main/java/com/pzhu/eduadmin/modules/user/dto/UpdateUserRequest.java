package com.pzhu.eduadmin.modules.user.dto;

import lombok.Data;

@Data
public class UpdateUserRequest {

    private String username;

    private String realName;

    private String phone;

    private String roleCode;
}
