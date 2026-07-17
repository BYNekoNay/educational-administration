package com.pzhu.eduadmin.modules.enrollment.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 家长视角的班级信息（用于在线报名选班）
 * 包含：班级名、教师名、起课时间、容量、当前人数、课次摘要
 */
@Data
public class ParentClassVO {

    private Long id;
    private String className;
    private Long teacherId;
    private String teacherName;
    private LocalDate startDate;
    private Integer maxStudentCount;
    private Integer currentStudentCount;
    private Integer status;
    /** 课次摘要：例 "周三/周五 19:00-20:30"，从 schedule_lesson 聚合得出 */
    private String scheduleSummary;
}
