package com.pzhu.eduadmin.modules.student.dto;

import lombok.Data;

/**
 * 家长联系方式（教师单条查看用）。
 * <p>只暴露"联系得上"所需的最小字段，不含家长账号、身份证等。
 */
@Data
public class ParentContactVO {

    private Long parentUserId;

    private String parentName;

    private String phone;
}
