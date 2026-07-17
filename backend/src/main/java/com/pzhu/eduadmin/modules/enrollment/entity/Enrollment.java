package com.pzhu.eduadmin.modules.enrollment.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("enrollment")
public class Enrollment {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    private Long parentUserId;

    private Long courseId;

    private Long classId;

    /** 1=待审核, 2=待缴费, 3=已完成, 4=已拒绝, 5=已失效(超时), 6=已退费 */
    private Integer status;

    private Long auditorId;

    private String auditRemark;

    private LocalDateTime holdExpireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer isDeleted;

    // ---- 关联名称（不存库）----
    @TableField(exist = false)
    private String studentName;

    @TableField(exist = false)
    private String parentName;

    @TableField(exist = false)
    private String courseName;

    @TableField(exist = false)
    private String className;

    @TableField(exist = false)
    private String auditorName;
}
