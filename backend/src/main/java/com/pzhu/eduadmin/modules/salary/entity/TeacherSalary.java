package com.pzhu.eduadmin.modules.salary.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** Bug #32 fix: 代课金额单独持久化，避免 totalAmount = base + substitute + bonus 中代课部分丢失 */
    private BigDecimal substituteAmount;

    private BigDecimal bonusAmount;

    private BigDecimal totalAmount;

    private Integer status;

    private LocalDateTime calcSnapshotTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String teacherName;
}
