package com.pzhu.eduadmin.modules.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("class_student")
public class ClassStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long studentId;

    private LocalDateTime joinTime;

    private Integer status;

    /** 转出/退出时间：还原"某课次当时是否在班" */
    private LocalDateTime leaveTime;

    /** 学员姓名（不持久化） */
    @TableField(exist = false)
    private String studentName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;
}
