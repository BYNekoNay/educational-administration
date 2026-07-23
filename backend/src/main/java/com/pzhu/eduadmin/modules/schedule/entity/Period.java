package com.pzhu.eduadmin.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("period")
public class Period {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer slotOrder;

    private LocalTime startTime;

    private LocalTime endTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
