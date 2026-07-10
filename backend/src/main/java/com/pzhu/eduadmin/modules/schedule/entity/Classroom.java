package com.pzhu.eduadmin.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("classroom")
public class Classroom {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer capacity;

    private String campus;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
