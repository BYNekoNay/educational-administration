package com.pzhu.eduadmin.modules.risk.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 流失预警名单行 VO。
 */
@Data
public class RiskStudentVO {

    private Long studentId;
    private String studentName;

    /** 当前在班班级（该学员在该班的预警行） */
    private Long classId;
    private String className;
    private Long courseId;
    private String courseName;

    private Integer riskScore;
    private String riskLevel;

    private Integer f1;
    private Integer f2;
    private Integer f3;
    private Integer f4;
    private Integer f5;

    /** 最近到课/迟到日期（null=从未到课） */
    private LocalDate lastAttendDate;

    private Integer scheduledCount28d;
    private Integer absentCount28d;
    private Double absenceRate28d;

    private BigDecimal remainingLessons;
    private BigDecimal totalLessons;
    private LocalDate expireDate;
    private Long daysToExpire;

    private String suggestedAction;

    /** 0-待跟进 1-已跟进 2-暂不跟进（null=从未标记，等同于待跟进） */
    private Integer followUpStatus;
    private String followUpRemark;
}
