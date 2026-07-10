package com.pzhu.eduadmin.modules.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("class_group")
public class ClassGroup {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private String className;

    private Long teacherId;

    private Integer maxStudentCount;

    private LocalDate startDate;

    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
