package com.pzhu.eduadmin.modules.schedule.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 教务拖拽快速调课请求（P1）。
 *
 * <p>语义：直接更新原课次的上课日期与起止时间（免审批、不置 status=4、
 * 不生成 sourceLessonId），仅保留班级/教师/教室/时长不变的时间微调。</p>
 */
@Data
public class QuickAdjustRequest {

    /** 目标上课日期（不早于今天） */
    @NotNull(message = "目标日期不能为空")
    private LocalDate lessonDate;

    /** 目标开始时间 */
    @NotNull(message = "目标开始时间不能为空")
    private LocalTime startTime;

    /** 目标结束时间（必须晚于 startTime，且不跨午夜） */
    @NotNull(message = "目标结束时间不能为空")
    private LocalTime endTime;

    /** 调整原因（可选，写入操作日志） */
    private String reason;
}
