package com.pzhu.eduadmin.common;

import com.pzhu.eduadmin.modules.course.mapper.ClassGroupMapper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.student.mapper.StudentMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 实体 ID → 名称解析工具。
 * <p>
 * 所有方法均绕过 {@code @TableLogic} 查询已软删实体，
 * 确保历史操作日志中的名称不丢失。
 * </p>
 */
@Component
@RequiredArgsConstructor
public class EntityNameResolver {

    private final StudentMapper studentMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final ClassGroupMapper classGroupMapper;

    /** 解析学员名称，未找到时降级为 "学员id=X" */
    public String getStudentName(Long id) {
        if (id == null) return "未知";
        List<Map<String, Object>> raw = studentMapper.selectNamesByIdsIncludeDeleted(Collections.singleton(id));
        if (raw.isEmpty()) return "学员id=" + id;
        return (String) raw.get(0).get("name");
    }

    /** 解析用户展示名（优先真实姓名，否则用户名） */
    public String getUserDisplayName(Long id) {
        if (id == null) return "未知";
        List<Map<String, Object>> raw = userMapper.selectNamesByIdsIncludeDeleted(Collections.singleton(id));
        if (raw.isEmpty()) return "用户id=" + id;
        Map<String, Object> m = raw.get(0);
        String realName = (String) m.get("real_name");
        if (realName != null && !realName.isBlank()) return realName;
        return (String) m.get("username");
    }

    /** 解析课程名称 */
    public String getCourseName(Long id) {
        if (id == null) return "未知";
        List<Map<String, Object>> raw = courseMapper.selectNamesByIdsIncludeDeleted(Collections.singleton(id));
        if (raw.isEmpty()) return "课程id=" + id;
        return (String) raw.get(0).get("name");
    }

    /** 解析班级名称 */
    public String getClassName(Long id) {
        if (id == null) return "未知";
        List<Map<String, Object>> raw = classGroupMapper.selectClassNamesByIdsIncludeDeleted(Collections.singleton(id));
        if (raw.isEmpty()) return "班级id=" + id;
        return (String) raw.get(0).get("class_name");
    }
}
