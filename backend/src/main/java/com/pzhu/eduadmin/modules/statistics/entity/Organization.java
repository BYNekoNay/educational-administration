package com.pzhu.eduadmin.modules.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("organization")
public class Organization {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotBlank(message = "机构名称不能为空")
    private String orgName;

    private String campus;

    private String contactPhone;

    private String address;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
