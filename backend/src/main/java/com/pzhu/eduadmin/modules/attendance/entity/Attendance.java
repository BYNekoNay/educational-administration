package com.pzhu.eduadmin.modules.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("attendance")
public class Attendance {

    @TableId(type = IdType.AUTO)
    private Long id;

    @NotNull(message = "课次ID不能为空")
    private Long lessonId;

    @NotNull(message = "学员ID不能为空")
    private Long studentId;

    private Integer status;

    private BigDecimal deductLessons;

    private LocalDateTime checkTime;

    @Size(max = 255, message = "备注长度不能超过255")
    private String remark;

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
    private String lessonInfo;
}
