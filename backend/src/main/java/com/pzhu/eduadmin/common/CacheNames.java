package com.pzhu.eduadmin.common;

/**
 * 缓存名称常量（P-05）。集中定义避免各模块字面量不一致导致 @Cacheable / @CacheEvict 失配。
 */
public final class CacheNames {

    /** 教务看板聚合（StatisticsServiceImpl.getDashboard） */
    public static final String DASHBOARD = "dashboard";

    /** 流失预警名单（RiskWarningServiceImpl.listRiskWarnings，按筛选条件分键） */
    public static final String RISK_WARNINGS = "riskWarnings";

    /** 流失预警机构维度汇总（RiskWarningServiceImpl.summary） */
    public static final String RISK_SUMMARY = "riskSummary";

    private CacheNames() {
    }
}
