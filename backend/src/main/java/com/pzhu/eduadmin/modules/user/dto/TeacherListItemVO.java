package com.pzhu.eduadmin.modules.user.dto;

import com.pzhu.eduadmin.modules.course.entity.Course;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 教师列表行 VO。
 * <p>状态语义（四态，读时计算，不落库）：
 * <ul>
 *   <li>status != 1               → 已离职 / 已停用</li>
 *   <li>status == 1, leave_date 为空        → 在职</li>
 *   <li>status == 1, leave_date 在今天之后  → 将于 X 日离职</li>
 *   <li>status == 1, leave_date 已到或已过  → 已过离职日未停用（需处理）</li>
 * </ul>
 */
@Data
public class TeacherListItemVO {

    private Long id;
    private String username;
    private String realName;
    private String phone;

    private LocalDate hireDate;
    private LocalDate leaveDate;

    private Integer status;
    private Integer mustChangePassword;

    /** 展示用状态文案 */
    private String statusLabel;

    /** ElTag 类型：success / warning / danger / info */
    private String statusType;

    private List<Long> specialtyCourseIds;
    private List<Course> specialties;
}
