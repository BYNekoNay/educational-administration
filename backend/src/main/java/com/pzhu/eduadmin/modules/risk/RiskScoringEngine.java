package com.pzhu.eduadmin.modules.risk;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 流失风险多因子打分引擎（纯计算、可解释、无 ML、无 Spring 依赖）。
 *
 * <p>因子与默认口径（均衡档）：
 * <ul>
 *   <li>F1 到课沉寂度：最近到课/迟到距今天数 D。D≤7→0；8–14→10；15–21→20；
 *       22–30→35；31–45→50；>45→70。班级近期无排课（防误报）或从未到课但班级在运转按沉默计。</li>
 *   <li>F2 近期缺勤倾向：近 28 天被排课且有考勤(status∈{1,2,4})课次中缺勤(status=4)占比 P。
 *       P=0→0；0&lt;P≤25%→8；25%&lt;P≤50%→16；P&gt;50%→30（请假不计）。</li>
 *   <li>F3 课时余量不足：对每个课时账户按剩余占比打分，取最高分项：
 *       r&gt;50%→0；25–50%→10；10–25%→20；r≤2 或 &lt;10%→35。</li>
 *   <li>F4 到期紧迫度：expireDate 距今天数 E。E&gt;60→0；31–60→6；15–30→12；
 *       1–14→20；已过期且 remaining&gt;0→25（无到期日 0 分）。</li>
 *   <li>F5 异常出勤模式（默认关）：近 8 周连续缺勤≥2 次或已批请假≥3 次 → 加 10。</li>
 * </ul>
 * 总分 = F1+F2+F3+F4(+F5)，各因子按其上限封顶，总分封顶 100。</p>
 */
public class RiskScoringEngine {

    /**
     * 计算单名学员（单班级维度）的风险评分。
     *
     * @param facts 打分输入事实
     * @param props 规则配置（可为 null，此时用默认均衡档）
     * @return 评分结果；0 分学员 riskLevel=NONE（不进入名单）
     */
    public RiskScoreResult score(RiskStudentFacts facts, RiskRuleProperties props) {
        RiskRuleProperties p = props != null ? props : new RiskRuleProperties();
        int capF1 = p.getFactorCap() != null ? p.getFactorCap().getF1() : 70;
        int capF2 = p.getFactorCap() != null ? p.getFactorCap().getF2() : 30;
        int capF3 = p.getFactorCap() != null ? p.getFactorCap().getF3() : 35;
        int capF4 = p.getFactorCap() != null ? p.getFactorCap().getF4() : 25;
        int capF5 = p.getFactorCap() != null ? p.getFactorCap().getF5() : 10;

        int f1 = Math.min(scoreF1(facts), capF1);
        int f2 = Math.min(scoreF2(facts), capF2);
        int f3 = Math.min(scoreF3(facts), capF3);
        int f4 = Math.min(scoreF4(facts), capF4);
        int f5 = p.isF5ExtraEnabled() ? Math.min(scoreF5(facts), capF5) : 0;

        int total = Math.min(f1 + f2 + f3 + f4 + f5, 100);

        String level = resolveLevel(total, p);
        String action = resolveSuggestedAction(level, f1, f2, f3, f4);

        AccountDetail worst = selectWorstAccount(facts, p);

        return RiskScoreResult.builder()
                .f1(f1)
                .f2(f2)
                .f3(f3)
                .f4(f4)
                .f5(f5)
                .total(total)
                .riskLevel(level)
                .suggestedAction(action)
                .worstRemainingLessons(worst.remaining)
                .worstTotalLessons(worst.total)
                .worstExpireDate(worst.expireDate)
                .daysToExpire(worst.expireDate != null
                        ? ChronoUnit.DAYS.between(LocalDate.now(), worst.expireDate)
                        : null)
                .build();
    }

    /** F1 到课沉寂度。班级近期无排课 → 0（防误报：机构不排课不算学员流失） */
    int scoreF1(RiskStudentFacts facts) {
        if (!facts.isClassHasRecentSchedule()) {
            return 0;
        }
        if (facts.getLastAttendDate() == null) {
            // 从未到课但班级在近期运转 → 视为高沉默
            return 70;
        }
        long days = ChronoUnit.DAYS.between(facts.getLastAttendDate(), LocalDate.now());
        if (days <= 7) return 0;
        if (days <= 14) return 10;
        if (days <= 21) return 20;
        if (days <= 30) return 35;
        if (days <= 45) return 50;
        return 70;
    }

    /** F2 近期缺勤倾向。分母为近 28 天有考勤记录(1/2/4)的课次数，请假(3)不计入缺勤也不计入分母 */
    int scoreF2(RiskStudentFacts facts) {
        if (facts.getScheduledCount28d() <= 0) {
            return 0;
        }
        double rate = facts.getAbsentCount28d() * 100.0 / facts.getScheduledCount28d();
        if (rate <= 0) return 0;
        if (rate <= 25) return 8;
        if (rate <= 50) return 16;
        return 30;
    }

    /** F3 课时余量不足：遍历学员全部账户，取最高分项 */
    int scoreF3(RiskStudentFacts facts) {
        int worst = 0;
        if (facts.getAccounts() != null) {
            for (RiskStudentFacts.AccountSnapshot account : facts.getAccounts()) {
                int s = scoreAccountF3(account);
                if (s > worst) worst = s;
            }
        }
        return worst;
    }

    private int scoreAccountF3(RiskStudentFacts.AccountSnapshot account) {
        BigDecimal total = account.getTotalLessons();
        BigDecimal remaining = account.getRemainingLessons();
        if (total == null || total.compareTo(BigDecimal.ZERO) <= 0
                || remaining == null || remaining.compareTo(BigDecimal.ZERO) < 0) {
            return 0;
        }
        BigDecimal ratio = remaining.divide(total, 4, RoundingMode.HALF_UP);
        if (remaining.compareTo(new BigDecimal("2")) <= 0
                || ratio.compareTo(new BigDecimal("0.10")) < 0) {
            return 35;
        }
        if (ratio.compareTo(new BigDecimal("0.25")) <= 0) {
            return 20;
        }
        if (ratio.compareTo(new BigDecimal("0.50")) <= 0) {
            return 10;
        }
        return 0;
    }

    /** F4 到期紧迫度：取学员账户中最高分项 */
    int scoreF4(RiskStudentFacts facts) {
        int worst = 0;
        if (facts.getAccounts() != null) {
            for (RiskStudentFacts.AccountSnapshot account : facts.getAccounts()) {
                int s = scoreAccountF4(account);
                if (s > worst) worst = s;
            }
        }
        return worst;
    }

    private int scoreAccountF4(RiskStudentFacts.AccountSnapshot account) {
        if (account.getExpireDate() == null) {
            return 0;
        }
        long days = ChronoUnit.DAYS.between(LocalDate.now(), account.getExpireDate());
        boolean hasRemaining = account.getRemainingLessons() != null
                && account.getRemainingLessons().compareTo(BigDecimal.ZERO) > 0;
        if (days < 0) {
            // 已过期：仅当仍有剩余课时才计入紧迫（无剩余课时由 F3 表达）
            return hasRemaining ? 25 : 0;
        }
        if (days > 60) return 0;
        if (days > 30) return 6;
        if (days > 14) return 12;
        return 20;
    }

    /** F5 异常出勤模式（默认关闭）：连续缺勤≥2 或已批请假≥3 */
    int scoreF5(RiskStudentFacts facts) {
        if (facts.isConsecutiveAbsenceStreak() || facts.getApprovedLeaveCount8w() >= 3) {
            return 10;
        }
        return 0;
    }

    private String resolveLevel(int total, RiskRuleProperties p) {
        if (total <= 0) {
            return RiskScoreResult.LEVEL_NONE;
        }
        if (total >= p.getHighThreshold()) {
            return RiskScoreResult.LEVEL_HIGH;
        }
        if (total >= p.getMediumThreshold()) {
            return RiskScoreResult.LEVEL_MEDIUM;
        }
        return RiskScoreResult.LEVEL_LOW;
    }

    /** 产品默认建议动作映射（教务可人工调整，文案仅作建议） */
    private String resolveSuggestedAction(String level, int f1, int f2, int f3, int f4) {
        if (RiskScoreResult.LEVEL_HIGH.equals(level)) {
            List<String> parts = new ArrayList<>();
            if (f3 >= 20 || f4 >= 20) {
                parts.add("续费/到期提醒");
            }
            if (f1 >= 20 || f2 >= 8) {
                parts.add("到课回访/出勤关怀");
            }
            if (parts.isEmpty()) {
                parts.add("优先联系");
            }
            return String.join(" + ", parts);
        }
        if (RiskScoreResult.LEVEL_MEDIUM.equals(level)) {
            return "观察 + 下次课后关注";
        }
        if (RiskScoreResult.LEVEL_LOW.equals(level)) {
            return "低风险观察";
        }
        return "";
    }

    /** 选择展示用"最差账户"：F3 分项最高；同分取到期更近者 */
    private AccountDetail selectWorstAccount(RiskStudentFacts facts, RiskRuleProperties p) {
        int worstF3 = scoreF3(facts);
        RiskStudentFacts.AccountSnapshot best = null;
        if (facts.getAccounts() != null) {
            for (RiskStudentFacts.AccountSnapshot account : facts.getAccounts()) {
                int f3 = scoreAccountF3(account);
                if (f3 < worstF3) {
                    continue;
                }
                if (best == null || betterThan(account, best)) {
                    best = account;
                }
            }
        }
        if (best == null) {
            return new AccountDetail(null, null, null);
        }
        return new AccountDetail(best.getRemainingLessons(), best.getTotalLessons(), best.getExpireDate());
    }

    private boolean betterThan(RiskStudentFacts.AccountSnapshot a, RiskStudentFacts.AccountSnapshot b) {
        LocalDate da = a.getExpireDate();
        LocalDate db = b.getExpireDate();
        if (da == null) return false;
        if (db == null) return true;
        return da.isBefore(db);
    }

    private record AccountDetail(BigDecimal remaining, BigDecimal total, LocalDate expireDate) {
    }
}
