package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.risk.RiskRuleProperties;
import com.pzhu.eduadmin.modules.risk.RiskScoreResult;
import com.pzhu.eduadmin.modules.risk.RiskScoringEngine;
import com.pzhu.eduadmin.modules.risk.RiskStudentFacts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 流失风险打分引擎纯单测。
 */
@DisplayName("流失风险打分引擎单元测试")
class RiskScoringEngineTest {

    private final RiskScoringEngine engine = new RiskScoringEngine();

    private RiskStudentFacts.AccountSnapshot account(BigDecimal remaining, BigDecimal total, LocalDate expire) {
        return RiskStudentFacts.AccountSnapshot.builder()
                .remainingLessons(remaining).totalLessons(total).expireDate(expire).build();
    }

    @Test
    @DisplayName("示例计算：最近到课18天前 + 近28天缺勤2/5 + 剩余3/20 + 20天后到期 → 68 高风险")
    void example_score68High() {
        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(18))
                .scheduledCount28d(5)
                .absentCount28d(2)
                .accounts(List.of(account(new BigDecimal("3"), new BigDecimal("20"), LocalDate.now().plusDays(20))))
                .build();

        RiskScoreResult result = engine.score(facts, new RiskRuleProperties());

        assertThat(result.getTotal()).isEqualTo(68);
        assertThat(result.getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_HIGH);
        assertThat(result.getF1()).isEqualTo(20);
        assertThat(result.getF2()).isEqualTo(16);
        assertThat(result.getF3()).isEqualTo(20);
        assertThat(result.getF4()).isEqualTo(12);
        assertThat(result.getSuggestedAction()).contains("续费/到期提醒").contains("到课回访/出勤关怀");
    }

    @Test
    @DisplayName("健康学员：近期到课+课时充足+无到期 → 0 分不入名单")
    void healthyStudent_zeroScore() {
        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(3))
                .scheduledCount28d(3)
                .absentCount28d(0)
                .accounts(List.of(account(new BigDecimal("18"), new BigDecimal("20"), LocalDate.now().plusDays(90))))
                .build();

        RiskScoreResult result = engine.score(facts, new RiskRuleProperties());

        assertThat(result.getTotal()).isZero();
        assertThat(result.getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_NONE);
    }

    @Test
    @DisplayName("防误报：班级近期无排课 → F1 沉寂度不计分")
    void noRecentClassSchedule_suppressesF1() {
        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(false) // 班级停排（机构放假等）
                .lastAttendDate(LocalDate.now().minusDays(100))
                .scheduledCount28d(0)
                .absentCount28d(0)
                .accounts(List.of(account(new BigDecimal("20"), new BigDecimal("20"), LocalDate.now().plusDays(90))))
                .build();

        RiskScoreResult result = engine.score(facts, new RiskRuleProperties());

        assertThat(result.getF1()).isZero();
        assertThat(result.getTotal()).isZero();
    }

    @Test
    @DisplayName("F1 沉寂度分档：8-14→10；15-21→20；22-30→35；31-45→50；>45→70")
    void f1_banding() {
        RiskRuleProperties props = new RiskRuleProperties();
        int[] days = {5, 10, 18, 26, 40, 60};
        int[] expected = {0, 10, 20, 35, 50, 70};
        for (int i = 0; i < days.length; i++) {
            RiskStudentFacts facts = RiskStudentFacts.builder()
                    .classHasRecentSchedule(true)
                    .lastAttendDate(LocalDate.now().minusDays(days[i]))
                    .build();
            assertThat(engine.score(facts, props).getF1())
                    .as("D=%d 天", days[i]).isEqualTo(expected[i]);
        }
    }

    @Test
    @DisplayName("F2 缺勤倾向分档：0→0；≤25→8；≤50→16；>50→30")
    void f2_banding() {
        RiskRuleProperties props = new RiskRuleProperties();
        int[][] cases = {{0, 4, 0}, {1, 4, 8}, {2, 5, 16}, {3, 5, 30}};
        for (int[] c : cases) {
            RiskStudentFacts facts = RiskStudentFacts.builder()
                    .classHasRecentSchedule(true)
                    .scheduledCount28d(c[1])
                    .absentCount28d(c[0])
                    .build();
            assertThat(engine.score(facts, props).getF2())
                    .as("缺勤 %d/%d", c[0], c[1]).isEqualTo(c[2]);
        }
    }

    @Test
    @DisplayName("F3 余量分档：≤2 或 <10%→35；10-25%→20；25-50%→10；>50%→0，多账户取最高分")
    void f3_bandingAndWorstAccount() {
        RiskRuleProperties props = new RiskRuleProperties();
        // 账户A剩余1节(≤2) →35；账户B剩余50%→10 → 取最高 35
        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .accounts(List.of(
                        account(new BigDecimal("1"), new BigDecimal("10"), null),
                        account(new BigDecimal("10"), new BigDecimal("20"), null)))
                .build();
        assertThat(engine.score(facts, props).getF3()).isEqualTo(35);

        RiskStudentFacts mid = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .accounts(List.of(account(new BigDecimal("3"), new BigDecimal("20"), null)))
                .build();
        assertThat(engine.score(mid, props).getF3()).isEqualTo(20);
    }

    @Test
    @DisplayName("F4 到期分档：>60→0；31-60→6；15-30→12；1-14→20；已过期且有剩余→25")
    void f4_banding() {
        RiskRuleProperties props = new RiskRuleProperties();
        int[] days = {90, 40, 20, 7};
        int[] expected = {0, 6, 12, 20};
        for (int i = 0; i < days.length; i++) {
            RiskStudentFacts facts = RiskStudentFacts.builder()
                    .classHasRecentSchedule(true)
                    .accounts(List.of(account(new BigDecimal("5"), new BigDecimal("10"), LocalDate.now().plusDays(days[i]))))
                    .build();
            assertThat(engine.score(facts, props).getF4())
                    .as("E=%d 天", days[i]).isEqualTo(expected[i]);
        }
        RiskStudentFacts expired = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .accounts(List.of(account(new BigDecimal("5"), new BigDecimal("10"), LocalDate.now().minusDays(3))))
                .build();
        assertThat(engine.score(expired, props).getF4()).isEqualTo(25);
    }

    @Test
    @DisplayName("F5 默认关闭；开启后命中连续缺勤/请假≥3 才加分")
    void f5_extraEnabledToggle() {
        RiskStudentFacts hit = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .consecutiveAbsenceStreak(true)
                .approvedLeaveCount8w(0)
                .build();

        RiskRuleProperties off = new RiskRuleProperties();
        assertThat(engine.score(hit, off).getF5()).isZero();

        RiskRuleProperties on = new RiskRuleProperties();
        on.setF5ExtraEnabled(true);
        assertThat(engine.score(hit, on).getF5()).isEqualTo(10);

        RiskStudentFacts leaves = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .consecutiveAbsenceStreak(false)
                .approvedLeaveCount8w(3)
                .build();
        assertThat(engine.score(leaves, on).getF5()).isEqualTo(10);
    }

    @Test
    @DisplayName("总分封顶 100")
    void total_cappedAt100() {
        RiskStudentFacts facts = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(200))   // F1=70
                .scheduledCount28d(4).absentCount28d(4)           // F2=30
                .accounts(List.of(account(new BigDecimal("0"), new BigDecimal("10"), LocalDate.now().minusDays(10)))) // F3=35 F4=25
                .build();

        RiskScoreResult result = engine.score(facts, new RiskRuleProperties());

        assertThat(result.getTotal()).isEqualTo(100);
    }

    @Test
    @DisplayName("档位判定：60→HIGH；30-59→MEDIUM；1-29→LOW；0→NONE")
    void levelBands() {
        RiskRuleProperties props = new RiskRuleProperties();
        // HIGH 边界：F1=50(D≈40) + F3=10(余量25-50%) = 60
        RiskStudentFacts high = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(40))
                .accounts(List.of(account(new BigDecimal("7.5"), new BigDecimal("20"), null)))
                .build();
        assertThat(engine.score(high, props).getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_HIGH);

        // MEDIUM 上界：F1=35 + F2=8 + F3=10 + F4=6 = 59
        RiskStudentFacts medium59 = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(25))
                .scheduledCount28d(4).absentCount28d(1)
                .accounts(List.of(account(new BigDecimal("7.5"), new BigDecimal("20"), LocalDate.now().plusDays(40))))
                .build();
        assertThat(engine.score(medium59, props).getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_MEDIUM);

        // MEDIUM 下界：F1=20 + F3=10 = 30
        RiskStudentFacts medium30 = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(18))
                .accounts(List.of(account(new BigDecimal("7.5"), new BigDecimal("20"), null)))
                .build();
        assertThat(engine.score(medium30, props).getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_MEDIUM);

        // LOW：F1=20 → 20
        RiskStudentFacts low = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(18))
                .build();
        assertThat(engine.score(low, props).getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_LOW);

        // NONE：总分 0
        RiskStudentFacts none = RiskStudentFacts.builder()
                .classHasRecentSchedule(true)
                .lastAttendDate(LocalDate.now().minusDays(3))
                .build();
        assertThat(engine.score(none, props).getRiskLevel()).isEqualTo(RiskScoreResult.LEVEL_NONE);
    }
}
