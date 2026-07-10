package com.pzhu.eduadmin.modules.salary.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("salary_rule")
public class SalaryRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private Long courseId;

    private BigDecimal lessonUnitPrice;

    private BigDecimal substituteRate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String courseName;
}
