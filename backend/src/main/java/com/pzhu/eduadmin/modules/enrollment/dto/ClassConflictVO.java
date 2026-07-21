package com.pzhu.eduadmin.modules.enrollment.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 班级时间冲突信息（用于家长报名前端预检）
 */
@Data
@AllArgsConstructor
public class ClassConflictVO {

    /** 冲突的目标班级 ID */
    private Long classId;

    /** 冲突班级名称 */
    private String className;

    /** 已报名的冲突班级名称 */
    private String conflictClassName;

    /** 冲突描述，例 "周三 09:00-10:00 与 09:30-10:30 重叠" */
    private String description;
}
