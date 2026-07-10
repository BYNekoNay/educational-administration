package com.pzhu.eduadmin.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("lesson_flow")
public class LessonFlow {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long accountId;

    private Long studentId;

    private Long lessonId;

    private Integer sourceType;

    private Long sourceId;

    private BigDecimal changeAmount;

    private Integer changeType;

    private BigDecimal beforeBalance;

    private BigDecimal afterBalance;

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
}
