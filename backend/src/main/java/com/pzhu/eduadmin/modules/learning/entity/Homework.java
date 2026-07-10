package com.pzhu.eduadmin.modules.learning.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("homework")
public class Homework {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long teacherId;

    private String content;

    private String attachmentUrl;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
