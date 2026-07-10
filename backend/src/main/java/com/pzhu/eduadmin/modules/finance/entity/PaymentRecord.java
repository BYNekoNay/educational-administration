package com.pzhu.eduadmin.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_record")
public class PaymentRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long enrollmentId;

    private Long studentId;

    private Long courseId;

    private BigDecimal lessonCount;

    private BigDecimal amount;

    private Integer payType;

    private LocalDateTime payTime;

    private Long operatorId;

    private String operatorRole;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String studentName;

    @TableField(exist = false)
    private String courseName;
}
