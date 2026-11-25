package com.pzhu.eduadmin.modules.finance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /**
     * 人工调整幂等键，仅 {@code source_type=5} 时由客户端生成。
     * 其余来源类型为 NULL；MySQL 唯一索引允许多个 NULL，故存量流水无需回填。
     */
    private String bizNo;

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
}
