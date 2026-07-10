package com.pzhu.eduadmin.modules.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("statistics_snapshot")
public class StatisticsSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate statDate;

    private String statType;

    private BigDecimal statValue;

    private String extraJson;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
