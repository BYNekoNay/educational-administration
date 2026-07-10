package com.pzhu.eduadmin.modules.salary.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("salary_adjustment")
public class SalaryAdjustment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherSalaryId;

    private BigDecimal adjustAmount;

    private String reason;

    private Long operatorId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
