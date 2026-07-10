package com.pzhu.eduadmin.modules.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CurrentUserResponse {

    private Long userId;
    private String username;
    private String realName;
    private String roleCode;
}
