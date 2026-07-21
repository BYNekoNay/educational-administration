package com.pzhu.eduadmin.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("schedule_lesson")
public class ScheduleLesson {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long classId;

    private Long teacherId;

    private Long classroomId;

    private LocalDate lessonDate;

    private LocalTime startTime;

    private LocalTime endTime;

    private Integer status;

    private Long sourceLessonId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String teacherName;

    @TableField(exist = false)
    private String classroomName;

    @TableField(exist = false)
    private String courseName;

    /** 请假状态（null=无请假, 1=请假中, 2=已通过） */
    @TableField(exist = false)
    private Integer leaveStatus;

    /** 课程ID（从 class_group.course_id 回查，前端按此分组） */
    @TableField(exist = false)
    private Long courseId;
}
