package com.pzhu.eduadmin.modules.enrollment.dto;

import com.pzhu.eduadmin.modules.course.entity.Course;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 家长报名页的一致性决策快照。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ParentEnrollmentSnapshotVO {

    private Long studentId;
    private LocalDateTime snapshotAt;
    private LocalDateTime snapshotExpiresAt;
    private String versionToken;
    private List<CourseDecision> courses;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CourseDecision {
        private Course course;
        private boolean enrolled;
        private Integer activeEnrollmentStatus;
        private LocalDateTime holdExpireTime;
        private boolean holdExpired;
        private int availableClassCount;
        private List<ParentClassVO> classes;
        private List<ClassConflictVO> conflicts;
    }
}
