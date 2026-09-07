package com.pzhu.eduadmin.modules.risk.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流失预警跟进状态（P4 决策 D5）。
 *
 * <p>与"每天实时重算的名单"配套：学员级"待跟进/已跟进/暂不跟进"闭环由本表记录，
 * 业务上同一学员仅一行（uk_student）。</p>
 */
@Data
@TableName("student_risk_followup")
public class StudentRiskFollowup {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_FOLLOWED = 1;
    public static final int STATUS_SKIP = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long studentId;

    /** 0-待跟进，1-已跟进，2-暂不跟进 */
    private Integer status;

    private String remark;

    private Long operatorId;

    private LocalDateTime followupTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
