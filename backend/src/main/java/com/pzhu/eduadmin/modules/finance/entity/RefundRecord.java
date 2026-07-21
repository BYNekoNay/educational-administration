package com.pzhu.eduadmin.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("refund_record")
public class RefundRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long paymentRecordId;

    private Long enrollmentId;

    private Long applicantId;

    private String applicantRole;

    private Long auditorId;

    private BigDecimal amount;

    private BigDecimal lessonCount;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String studentName;

    @TableField(exist = false)
    private String applicantName;
}
