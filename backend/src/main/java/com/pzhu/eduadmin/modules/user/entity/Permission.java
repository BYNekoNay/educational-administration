package com.pzhu.eduadmin.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("permission")
public class Permission {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String permissionCode;

    private String path;

    private Integer type;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
