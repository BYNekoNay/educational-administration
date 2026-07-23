package com.pzhu.eduadmin.modules.schedule.dto;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 调课申请列表 VO，含课次关联信息（课程、班级、教室、时段等）
 */
@Data
public class AdjustRequestVO {

    // ---- 调课申请字段 ----
    private Long id;
    private Long lessonId;
    private LocalDateTime expectTime;
    private String reason;
    private Integer status;
    private String auditRemark;
    private LocalDateTime createTime;

    // ---- 课次关联字段 ----
    private LocalDate lessonDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String className;
    private String courseName;
    private String classroomName;
    private String teacherName;

    // ---- 时段字段 ----
    private Long periodId;
    private Integer periodCount;
    private String periodName;
}
