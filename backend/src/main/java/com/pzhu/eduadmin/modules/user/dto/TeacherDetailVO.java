package com.pzhu.eduadmin.modules.user.dto;

import com.pzhu.eduadmin.modules.course.entity.Course;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 教师详情 VO：档案 + 当前任教 + 历史任教。
 */
@Data
public class TeacherDetailVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;

    private LocalDate hireDate;
    private LocalDate leaveDate;

    private Integer status;
    private Integer mustChangePassword;

    private String statusLabel;
    private String statusType;

    private List<Long> specialtyCourseIds;
    private List<Course> specialties;

    /** 当前任教班级（class_group.teacher_id = 该教师 且 status = 1） */
    private List<TeachingClassVO> currentClasses = new ArrayList<>();

    /**
     * 历史任教：从 schedule_lesson 中 status=2 的已完成课次回溯，
     * 已排除当前仍在任教的班级（那些在 currentClasses 里）。
     */
    private List<TeachingClassVO> historyClasses = new ArrayList<>();

    @Data
    public static class TeachingClassVO {

        private Long classId;
        private String className;
        private String courseName;

        /** 当前任教：在班学员数 */
        private Integer studentCount;

        /** 历史任教：该班已完成课次数 */
        private Integer lessonCount;

        /** 历史任教：最后一节课日期 */
        private LocalDate lastLessonDate;
    }
}
