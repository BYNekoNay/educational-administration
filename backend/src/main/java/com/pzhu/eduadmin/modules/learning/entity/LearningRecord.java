package com.pzhu.eduadmin.modules.learning.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("learning_record")
public class LearningRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long studentId;

    private String teacherComment;

    private String growthTag;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;
}
