package com.pzhu.eduadmin.modules.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.pzhu.eduadmin.modules.course.entity.Course;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@TableName("user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    @JsonIgnore
    private String password;

    private String realName;

    private String phone;

    private String roleCode;

    private Integer status;

    private LocalDateTime lastLoginTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    @JsonIgnore
    private Integer isDeleted;

    /** Token 版本号：禁用用户或变更角色时递增，使旧 Token 失效 */
    @JsonIgnore
    private Integer version;

    /** 1-需强制修改密码（密码非本人设置，如管理员代设的初始密码） */
    @JsonIgnore
    private Integer mustChangePassword;

    /** 入职日期（仅 TEACHER 使用） */
    private java.time.LocalDate hireDate;

    /** 离职生效日；NULL=在职，<=今天 视为已离职（仅 TEACHER 使用） */
    private java.time.LocalDate leaveDate;

    // ---- 关联数据（不持久化） ----

    /** 教学特长课程ID列表（仅 TEACHER 角色使用） */
    @TableField(exist = false)
    private List<Long> specialtyCourseIds;

    /** 教学特长课程对象列表（仅 TEACHER 角色使用） */
    @TableField(exist = false)
    private List<Course> specialties;
}
