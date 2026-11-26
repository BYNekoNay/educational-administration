package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PasswordPolicy;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.Role;
import com.pzhu.eduadmin.modules.user.entity.TeacherCourse;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.RoleMapper;
import com.pzhu.eduadmin.modules.user.mapper.TeacherCourseMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final OperationLogService operationLogService;
    private final EntityNameResolver nameResolver;
    private final TeacherCourseMapper teacherCourseMapper;
    private final CourseMapper courseMapper;
    private final NotificationService notificationService;
    private final com.pzhu.eduadmin.security.LoginAttemptService loginAttemptService;

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
        // 校验角色编码是否存在
        if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, request.getRoleCode())) == 0) {
            throw new BusinessException(400, "角色编码不存在: " + request.getRoleCode());
        }

        User user = new User();
        user.setUsername(request.getUsername());
        user.setPassword(new BCryptPasswordEncoder().encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setRoleCode(request.getRoleCode());
        user.setStatus(1);
        // 教师入职日（展示字段）；其他角色传入忽略
        if ("TEACHER".equals(request.getRoleCode()) && request.getHireDate() != null) {
            user.setHireDate(request.getHireDate());
        }

        // 捕获唯一键冲突，防止并发注册 TOCTOU 竞态
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException e) {
            throw new BusinessException(409, "用户名已存在");
        }

        // 若角色为教师，保存教学特长
        if ("TEACHER".equals(request.getRoleCode())) {
            saveSpecialties(user.getId(), request.getSpecialtyCourseIds());
            fillUserSpecialties(user);
        }

        operationLogService.log("用户管理", "新增用户（用户=" + user.getUsername() + "）");

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
        boolean usernameChanged = request.getUsername() != null && !request.getUsername().equals(user.getUsername());
        if (usernameChanged) {
            User existing = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
            if (existing != null && !existing.getId().equals(id)) {
                throw new BusinessException(409, "用户名已存在");
            }
            user.setUsername(request.getUsername());
        }
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        // 入职日仅对教师角色生效；if != null 范式下只能改不能清空（可接受）
        if (request.getHireDate() != null && "TEACHER".equals(request.getRoleCode() != null ? request.getRoleCode() : user.getRoleCode())) {
            user.setHireDate(request.getHireDate());
        }
        if (request.getRoleCode() != null && !request.getRoleCode().equals(user.getRoleCode())) {
            // 校验角色编码是否存在
            if (roleMapper.selectCount(new LambdaQueryWrapper<Role>().eq(Role::getRoleCode, request.getRoleCode())) == 0) {
                throw new BusinessException(400, "角色编码不存在: " + request.getRoleCode());
            }
            // 保护最后一个 SUPER_ADMIN，角色降级会导致系统永久不可管理
            // （M16 仅在 updateUserStatus 中防护，此处补齐角色变更路径）
            if ("SUPER_ADMIN".equals(user.getRoleCode()) && !"SUPER_ADMIN".equals(request.getRoleCode())) {
                Long activeSuperAdminCount = userMapper.selectCount(
                        new LambdaQueryWrapper<User>()
                                .eq(User::getRoleCode, "SUPER_ADMIN")
                                .eq(User::getStatus, 1)
                                .ne(User::getId, id));
                if (activeSuperAdminCount == 0) {
                    throw new BusinessException(400, "不能变更最后一个超级管理员的角色");
                }
            }
            user.setRoleCode(request.getRoleCode());
            roleChanged = true;
        }
        // 角色或用户名变更时递增 version 使旧 Token 失效
        // 原子 SQL 递增，防止并发操作丢失递增
        if (roleChanged || usernameChanged) {
            userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                    .eq(User::getId, id)
                    .setSql("version = version + 1"));
        }
        // 防止读改写覆盖并发操作：仅更新可编辑字段（username/realName/phone/roleCode），
        // 避免 updateById 将 selectById 加载的陈旧 version/status/lastLoginTime 写回，
        // 覆盖禁用/重置密码/登录等并发操作的递增
        User update = new User();
        update.setId(id);
        if (usernameChanged) update.setUsername(user.getUsername());
        if (request.getRealName() != null) update.setRealName(user.getRealName());
        if (request.getPhone() != null) update.setPhone(user.getPhone());
        if (request.getHireDate() != null) update.setHireDate(user.getHireDate());
        if (roleChanged) update.setRoleCode(user.getRoleCode());
        userMapper.updateById(update);

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

        operationLogService.log("用户管理", "编辑用户（用户=" + user.getUsername() + "）");

        user.setPassword(null);
        return user;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(Long id, Integer status) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 保护最后一个 SUPER_ADMIN，防止系统永久不可管理
        if (status != 1 && "SUPER_ADMIN".equals(user.getRoleCode())) {
            Long activeSuperAdminCount = userMapper.selectCount(
                    new LambdaQueryWrapper<User>()
                            .eq(User::getRoleCode, "SUPER_ADMIN")
                            .eq(User::getStatus, 1)
                            .ne(User::getId, id));
            if (activeSuperAdminCount == 0) {
                throw new BusinessException(400, "不能禁用最后一个超级管理员");
            }
        }
        // 原子 SQL 递增 version，防止并发操作丢失递增导致 Token 失效机制被绕过
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getStatus, status)
                .setSql("version = version + 1"));

        operationLogService.log("用户管理", (status == 1 ? "启用用户" : "禁用用户") + "（用户=" + nameResolver.getUserDisplayName(id) + "）");
    }

    @Override
    public boolean unlockLoginLock(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        boolean wasLocked = loginAttemptService.unlockUser(user.getUsername());
        operationLogService.log("用户管理", "解锁登录锁定（用户=" + user.getUsername()
                + "，此前" + (wasLocked ? "处于锁定态" : "未锁定") + "）");
        return wasLocked;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, String newPassword) {
        // 统一强度策略：非空/≥6位/≤72字节/字母+数字组合（收敛自本方法与 AuthService 的重复校验）
        PasswordPolicy.validate(newPassword);
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 管理端重置不允许设成与当前密码相同的值（第六轮决策①）
        if (new BCryptPasswordEncoder().matches(newPassword, user.getPassword())) {
            throw new BusinessException(400, "新密码不能与当前密码相同");
        }
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        // 原子 SQL 递增 version，防止并发操作丢失递增。
        // must_change_password = 1 标记"密码不是本人设置的"：教师下次登录后会被
        // JwtInterceptor 强制跳改密，同时登录失败提示里会多一句"如密码刚被管理员重置"。
        userMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<User>()
                .eq(User::getId, id)
                .set(User::getPassword, new BCryptPasswordEncoder().encode(newPassword))
                .set(User::getMustChangePassword, 1)
                .setSql("version = version + 1"));
        // 审计到人 + 目标对象，便于事后回答"谁在什么时候重置了谁的密码"
        operationLogService.log("用户管理", "重置密码（用户=" + user.getUsername() + "）", "USER", id);

        // 通知本人。被重置的用户此刻已被踢下线，站内信只在"他还有别的设备在线"时看得到；
        // 真正必然被看到的是登录失败时的统一提示。
        try {
            com.pzhu.eduadmin.modules.notification.entity.Notification notice =
                    new com.pzhu.eduadmin.modules.notification.entity.Notification();
            notice.setUserId(id);
            notice.setType("ACCOUNT");
            notice.setTitle("登录密码已被重置");
            notice.setContent("您的登录密码已被管理员重置，如非本人申请请联系机构教务。");
            notificationService.send(id, notice);
        } catch (Exception e) {
            // 通知失败不影响改密本身（密码已经生效）
            log.warn("重置密码通知发送失败: userId={}", id, e);
        }
    }

    // ---- 教师教学特长管理 ----

    /** 保存教师特长课程（全量替换：先物理清后插，避免 @TableLogic 唯一键冲突） */
    private void saveSpecialties(Long userId, List<Long> courseIds) {
        teacherCourseMapper.realDeleteByUserId(userId);
        if (courseIds != null && !courseIds.isEmpty()) {
            // 去重并剔除 null，避免重复插入触发唯一键冲突 / 插入非法空 courseId
            java.util.Set<Long> seen = new java.util.LinkedHashSet<>();
            for (Long cid : courseIds) {
                if (cid == null || !seen.add(cid)) continue;
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
