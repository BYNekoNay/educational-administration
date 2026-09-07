package com.pzhu.eduadmin.modules.risk;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 风险评分结果（纯计算产物）。
 */
@Data
@Builder
public class RiskScoreResult {

    /** 结果为空（不进入名单） */
    public static final String LEVEL_NONE = "NONE";
    public static final String LEVEL_LOW = "LOW";
    public static final String LEVEL_MEDIUM = "MEDIUM";
    public static final String LEVEL_HIGH = "HIGH";

    private int f1;
    private int f2;
    private int f3;
    private int f4;
    private int f5;
    private int total;

    /** HIGH / MEDIUM / LOW / NONE（0 分） */
    private String riskLevel;

    /** 建议动作文案（产品默认映射，教务可人工调整） */
    private String suggestedAction;

    /** 最差账户信息（供 VO 展示 F3/F4 依据） */
    private BigDecimal worstRemainingLessons;
    private BigDecimal worstTotalLessons;
    private java.time.LocalDate worstExpireDate;
    private Long daysToExpire;
}
