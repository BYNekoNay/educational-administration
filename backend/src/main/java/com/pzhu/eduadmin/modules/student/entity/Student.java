package com.pzhu.eduadmin.modules.student.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("student")
public class Student {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private Integer gender;

    private LocalDate birthday;

    private String school;

    private String contactPhone;

    /** 1=正常, 4=已退班 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;

    /** 绑定的家长姓名（不持久化，回填用，多个家长用顿号分隔） */
    @TableField(exist = false)
    private String parentName;
}
