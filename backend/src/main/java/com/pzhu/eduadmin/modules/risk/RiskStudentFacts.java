package com.pzhu.eduadmin.modules.risk;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * 单名学员在某在班班级维度下的风险打分输入事实（纯数据，供 {@link RiskScoringEngine} 计算）。
 */
@Data
@Builder
public class RiskStudentFacts {

    /** 班级近 absenceLookbackDays 天是否有排课（status in 1,2）。false 时 F1 沉寂度不计分（防误报） */
    private boolean classHasRecentSchedule;

    /** 最近一次到课/迟到日期（null 表示从未到课） */
    private LocalDate lastAttendDate;

    /** 近 attendanceWindowDays 天被排课且产生考勤(status in 1,2,4)的课次数（F2 分母） */
    private int scheduledCount28d;

    /** 近 attendanceWindowDays 天缺勤(status=4)次数（请假不计） */
    private int absentCount28d;

    /** 学员全部课时账户快照（F3 余量 / F4 到期按最差账户计） */
    @Builder.Default
    private List<AccountSnapshot> accounts = Collections.emptyList();

    /** F5：近 8 周存在连续缺勤 >= 2 次 */
    private boolean consecutiveAbsenceStreak;

    /** F5：近 8 周已批准请假 >= 3 次 */
    private int approvedLeaveCount8w;

    /** 单个课时账户快照 */
    @Data
    @Builder
    public static class AccountSnapshot {
        private BigDecimal remainingLessons;
        private BigDecimal totalLessons;
        private LocalDate expireDate;
    }
}
