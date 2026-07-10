package com.pzhu.eduadmin.modules.salary.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("teacher_salary")
public class TeacherSalary {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private String salaryMonth;

    private BigDecimal lessonCount;

    private BigDecimal substituteCount;

    private BigDecimal baseAmount;

    private BigDecimal bonusAmount;

    private BigDecimal totalAmount;

    private Integer status;

    private LocalDateTime calcSnapshotTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String teacherName;
}
