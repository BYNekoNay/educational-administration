package com.pzhu.eduadmin.modules.statistics.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("operation_log")
public class OperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long operatorId;

    @TableField(exist = false)
    private String operatorName;

    private String module;

    private String operation;

    private String ip;

    /** 操作对象类型，如 STUDENT / USER；为空表示仅记录动作 */
    private String targetType;

    /** 操作对象ID，用于按对象反查"谁看过/改过" */
    private Long targetId;

    /** 同人同对象同日聚合次数（非 1 时说明发生了重复查看） */
    private Integer hitCount;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
