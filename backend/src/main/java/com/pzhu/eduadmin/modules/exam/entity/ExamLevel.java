package com.pzhu.eduadmin.modules.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("exam_level")
public class ExamLevel {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String levelName;

    private LocalDate examDate;

    private BigDecimal fee;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
