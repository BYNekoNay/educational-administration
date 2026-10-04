package com.pzhu.eduadmin.modules.schedule.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("schedule_adjust_request")
public class ScheduleAdjustRequest {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long lessonId;

    private Long applicantId;

    @Size(max = 255, message = "调课原因长度不能超过255")
    private String reason;

    private LocalDateTime expectTime;

    /** 1=待审核, 2=已通过, 3=已驳回 */
    private Integer status;

    private Long auditorId;

    @Size(max = 255, message = "审核备注长度不能超过255")
    private String auditRemark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @JsonIgnore
    @TableLogic
    private Integer isDeleted;
}
