package com.pzhu.eduadmin.modules.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 课程名称（不持久化） */
    @TableField(exist = false)
    private String courseName;

    /** 教师姓名（不持久化） */
    @TableField(exist = false)
    private String teacherName;

    /** 当前在班人数（不持久化，status=1 学员数） */
    @TableField(exist = false)
    private Integer currentStudentCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;
}
