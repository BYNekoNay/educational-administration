package com.pzhu.eduadmin.modules.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("parent_student")
public class ParentStudent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long parentUserId;

    private Long studentId;

    private String relation;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableLogic
    private Integer isDeleted;
}
