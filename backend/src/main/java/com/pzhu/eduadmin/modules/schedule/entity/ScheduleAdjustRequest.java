package com.pzhu.eduadmin.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_adjust_request")
public class ScheduleAdjustRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long applicantId;

    private String reason;

    private LocalDateTime expectTime;

    private Integer status;

    private Long auditorId;

    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;
}
