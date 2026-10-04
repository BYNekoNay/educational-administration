package com.pzhu.eduadmin.modules.schedule.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class AutoScheduleRequest {

    @NotNull private Long classId;
    @NotNull private Long teacherId;
    private Long classroomId;
    @NotNull private LocalDate startDate;
    @NotNull private LocalDate endDate;
    @NotNull private LocalTime startTime;
    @NotNull private LocalTime endTime;
    @NotNull(message = "课时数量不能为空")
    @Positive private Integer lessonCount;
    private List<@Min(1) @Max(7) Integer> weekdays;

    /** 教师单日最大课次数（可选；null=不限制）。达到上限的候选日期会被跳过，避免把同一教师的一天排满 */
    @Positive(message = "教师单日课次上限必须为正数")
    private Integer maxLessonsPerDay;
}
