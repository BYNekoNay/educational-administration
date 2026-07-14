package com.pzhu.eduadmin.modules.exam.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("exam_signup")
public class ExamSignup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long examId;

    private Long studentId;

    private BigDecimal score;

    private String certificateNo;

    private Integer status;

    /** 考级项目名称（不持久化） */
    @TableField(exist = false)
    private String examName;

    /** 学员姓名（不持久化） */
    @TableField(exist = false)
    private String studentName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
