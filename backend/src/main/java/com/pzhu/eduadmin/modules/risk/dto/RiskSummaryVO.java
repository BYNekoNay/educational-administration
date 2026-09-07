package com.pzhu.eduadmin.modules.risk.dto;

import lombok.Data;

import java.util.List;

/**
 * 机构维度流失预警汇总。
 */
@Data
public class RiskSummaryVO {

    private long total;
    private long high;
    private long medium;
    private long low;

    /** 分档明细 [{level:'HIGH', count:..}, ...] */
    private List<LevelCount> byLevel;

    @Data
    public static class LevelCount {
        private String level;
        private long count;

        public static LevelCount of(String level, long count) {
            LevelCount item = new LevelCount();
            item.setLevel(level);
            item.setCount(count);
            return item;
        }
    }
}
