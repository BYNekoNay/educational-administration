package com.pzhu.eduadmin.modules.risk;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 流失预警打分规则配置（前缀 app.risk，默认"均衡档"）。
 *
 * <p>本期只实现默认均衡档，保守/灵敏档作为参数预留：全部阈值收敛为可配置字段，
 * 不提供管理端 UI 切换（决策 D4）。改动配置即可整体平移判档线，无需改代码。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.risk")
public class RiskRuleProperties {

    /** 规则引擎开关（默认开） */
    private boolean enabled = true;

    /** 配置档位标识：balanced(均衡,默认) | conservative | sensitive（仅记录，本期不实现 UI 切换） */
    private String preset = "balanced";

    /** F5 异常出勤模式加分是否启用（均衡档默认关闭） */
    private boolean f5ExtraEnabled = false;

    /** 高风险档位线（默认 60） */
    private int highThreshold = 60;

    /** 中风险档位线（默认 30；低于该线且总分>=1 为低风险） */
    private int mediumThreshold = 30;

    /** F2 缺勤倾向统计窗口（天，默认 28） */
    private int attendanceWindowDays = 28;

    /** 班级近期排课判定窗口 / F1 沉寂判定上限（天，默认 45）。用于防误报：班级该窗口无排课则 F1 不计分 */
    private int absenceLookbackDays = 45;

    /** 各因子得分上限 */
    private FactorCap factorCap = new FactorCap();

    @Data
    public static class FactorCap {
        private int f1 = 70;
        private int f2 = 30;
        private int f3 = 35;
        private int f4 = 25;
        private int f5 = 10;
    }
}
