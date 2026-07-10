package com.pzhu.eduadmin.modules.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization")
public class Organization {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orgName;

    private String campus;

    private String contactPhone;

    private String address;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
