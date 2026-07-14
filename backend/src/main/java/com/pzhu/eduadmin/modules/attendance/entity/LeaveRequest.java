package com.pzhu.eduadmin.modules.attendance.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("leave_request")
public class LeaveRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long parentUserId;

    private LocalDate lessonDate;

    private Long scheduleId;

    private String reason;

    /** 1=pending, 2=approved, 3=rejected */
    private Integer status;

    private Long auditUserId;

    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ---- 关联名称（不存库）----

    @TableField(exist = false)
    private String studentName;

    @TableField(exist = false)
    private String courseName;
}
