package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.TeacherCourse;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.TeacherCourseMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final OperationLogMapper operationLogMapper;
    private final TeacherCourseMapper teacherCourseMapper;
    private final CourseMapper courseMapper;

    private static final Map<String, SFunction<User, ?>> USER_SORT_MAP = Map.of(
            "id", User::getId,
            "username", User::getUsername,
            "realName", User::getRealName,
            "createTime", User::getCreateTime
    );

    @Override
    public Page<User> pageUsers(int pageNum, int pageSize, String keyword, String sortField, String sortOrder) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        QueryHelper.applyKeyword(wrapper, keyword, User::getUsername, User::getRealName, User::getPhone);
        QueryHelper.applySort(wrapper, sortField, sortOrder, USER_SORT_MAP, () -> wrapper.orderByDesc(User::getCreateTime));
        Page<User> page = userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        // 为教师填充教学特长
        page.getRecords().forEach(user -> {
            if ("TEACHER".equals(user.getRoleCode())) {
                fillUserSpecialties(user);
            }
        });
        page.getRecords().forEach(u -> u.setPassword(null));
        return page;
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User createUser(CreateUserRequest request) {
        if (userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername())) != null) {
            throw new BusinessException("用户名已存在");
        }
        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setRoleCode(request.getRoleCode());
        user.setStatus(1);
        userMapper.insert(user);

        // 若角色为教师，保存教学特长
        if ("TEACHER".equals(request.getRoleCode())) {
            saveSpecialties(user.getId(), request.getSpecialtyCourseIds());
            fillUserSpecialties(user);
        }

        logOperation("用户管理", "新增用户(username=" + user.getUsername() + ")");

        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        boolean roleChanged = false;
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getRoleCode() != null && !request.getRoleCode().equals(user.getRoleCode())) {
            user.setRoleCode(request.getRoleCode());
            roleChanged = true;
        }
        // 角色变更时递增 version 使旧 Token 失效
        if (roleChanged) {
            user.setVersion((user.getVersion() != null ? user.getVersion() : 0) + 1);
        }
        userMapper.updateById(user);

        // 处理角色变更时的教学特长
        if ("TEACHER".equals(user.getRoleCode())) {
            if (request.getSpecialtyCourseIds() != null) {
                saveSpecialties(id, request.getSpecialtyCourseIds());
            }
        } else {
            // 角色不再是教师时，清理旧的特长关联
            teacherCourseMapper.realDeleteByUserId(id);
        }
        fillUserSpecialties(user);

        logOperation("用户管理", "编辑用户(id=" + id + ")");

        user.setPassword(null);
        return user;
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setStatus(status);
        // 递增 version 使旧 Token 失效（禁用/启用时）
        user.setVersion((user.getVersion() != null ? user.getVersion() : 0) + 1);
        userMapper.updateById(user);

        logOperation("用户管理", status == 1 ? "启用用户(id=" + id + ")" : "禁用用户(id=" + id + ")");
    }

    @Override
    public void resetPassword(Long id, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new BusinessException(400, "新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new BusinessException(400, "新密码长度不能少于6位");
        }
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        user.setPassword(new BCryptPasswordEncoder().encode(newPassword));
        user.setVersion((user.getVersion() != null ? user.getVersion() : 0) + 1);
        userMapper.updateById(user);
        logOperation("用户管理", "重置密码(id=" + id + ", username=" + user.getUsername() + ")");
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        LoginUser operator = CurrentUserHolder.get();
        log.setOperatorId(operator != null ? operator.getUserId() : 0L);
        log.setModule(module);
        log.setOperation(operation);
        log.setIp(com.pzhu.eduadmin.common.IpUtil.getCurrentIp());
        operationLogMapper.insert(log);
    }

    // ---- 教师教学特长管理 ----

    /** 保存教师特长课程（全量替换：先物理清后插，避免 @TableLogic 唯一键冲突） */
    private void saveSpecialties(Long userId, List<Long> courseIds) {
        teacherCourseMapper.realDeleteByUserId(userId);
        if (courseIds != null && !courseIds.isEmpty()) {
            for (Long cid : courseIds) {
                TeacherCourse tc = new TeacherCourse();
                tc.setUserId(userId);
                tc.setCourseId(cid);
                teacherCourseMapper.insert(tc);
            }
        }
    }

    /** 填充单个教师的 specialties 字段 */
    private void fillUserSpecialties(User user) {
        if (!"TEACHER".equals(user.getRoleCode())) return;
        List<Long> courseIds = teacherCourseMapper.selectList(
                        new LambdaQueryWrapper<TeacherCourse>().eq(TeacherCourse::getUserId, user.getId()))
                .stream().map(TeacherCourse::getCourseId).collect(Collectors.toList());
        user.setSpecialtyCourseIds(courseIds);
        if (!courseIds.isEmpty()) {
            List<Course> courses = courseMapper.selectBatchIds(courseIds);
            user.setSpecialties(courses);
        } else {
            user.setSpecialties(Collections.emptyList());
        }
    }

    @Override
    public List<Long> getSpecialtyCourseIds(Long teacherId) {
        return teacherCourseMapper.selectList(
                        new LambdaQueryWrapper<TeacherCourse>().eq(TeacherCourse::getUserId, teacherId))
                .stream().map(TeacherCourse::getCourseId).collect(Collectors.toList());
    }

    @Override
    public void fillTeachersSpecialties(List<User> teachers) {
        if (teachers == null || teachers.isEmpty()) return;
        Set<Long> userIds = teachers.stream().map(User::getId).collect(Collectors.toSet());

        // 批量加载所有 teacher_course 关联
        List<TeacherCourse> allMappings = teacherCourseMapper.selectList(
                new LambdaQueryWrapper<TeacherCourse>().in(TeacherCourse::getUserId, userIds));

        Map<Long, List<Long>> teacherCourseMap = allMappings.stream()
                .collect(Collectors.groupingBy(TeacherCourse::getUserId,
                        Collectors.mapping(TeacherCourse::getCourseId, Collectors.toList())));

        // 加载所有引用的课程
        Set<Long> allCourseIds = allMappings.stream().map(TeacherCourse::getCourseId).collect(Collectors.toSet());
        Map<Long, Course> courseMap;
        if (!allCourseIds.isEmpty()) {
            courseMap = courseMapper.selectBatchIds(allCourseIds).stream()
                    .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));
        } else {
            courseMap = Collections.emptyMap();
        }

        for (User teacher : teachers) {
            List<Long> courseIds = teacherCourseMap.getOrDefault(teacher.getId(), Collections.emptyList());
            teacher.setSpecialtyCourseIds(courseIds);
            teacher.setSpecialties(courseIds.stream().map(courseMap::get).filter(Objects::nonNull).collect(Collectors.toList()));
        }
    }
}
