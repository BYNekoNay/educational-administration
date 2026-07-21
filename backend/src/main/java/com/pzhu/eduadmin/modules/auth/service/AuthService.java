package com.pzhu.eduadmin.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.CurrentUserResponse;
import com.pzhu.eduadmin.modules.user.dto.LoginRequest;
import com.pzhu.eduadmin.modules.user.dto.LoginResponse;
import com.pzhu.eduadmin.modules.user.dto.MenuTreeNode;
import com.pzhu.eduadmin.modules.user.dto.RegisterRequest;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.MenuMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.UserService;
import com.pzhu.eduadmin.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 登录鉴权服务实现：密码校验、Token 签发、用户信息查询。
 * 对应 docs/11-后端开发详细文档.md §2、docs/08-开发指南.md §2.2 分层约定。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final com.pzhu.eduadmin.modules.user.service.RoleService roleService;
    private final MenuMapper menuMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 登录失败计数（key = username），用于暴力破解防护 */
    private final ConcurrentHashMap<String, LoginAttemptInfo> loginAttempts = new ConcurrentHashMap<>();

    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 15 * 60 * 1000L; // 15 分钟

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();

        // 暴力破解防护：检查是否被锁定
        LoginAttemptInfo attemptInfo = loginAttempts.get(username);
        if (attemptInfo != null && attemptInfo.isLocked()) {
            throw new BusinessException(429, "账号已锁定，请 15 分钟后再试");
        }

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            recordFailedAttempt(username);
            throw new BusinessException("用户名或密码错误");
        }
        // H10 fix: 先检查账号状态再验证密码，防止攻击者确认禁用账号的密码
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            recordFailedAttempt(username);
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginAttempts.remove(username);

        // H1 fix: 仅更新 lastLoginTime，避免 updateById 将 stale status/version 写回覆盖并发操作
        userMapper.update(null, new LambdaUpdateWrapper<User>()
                .eq(User::getId, user.getId())
                .set(User::getLastLoginTime, LocalDateTime.now()));

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRoleCode(),
                user.getVersion() != null ? user.getVersion() : 0);
        List<String> permissions = loadPermissions(user.getRoleCode());
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(),
                user.getRoleCode(), permissions);
    }

    @Override
    public LoginResponse register(RegisterRequest request) {
        // 移动端自助注册：角色固定为 PARENT，不信任前端传入的角色
        CreateUserRequest createReq = new CreateUserRequest();
        createReq.setUsername(request.getUsername());
        createReq.setPassword(request.getPassword());
        createReq.setRealName(request.getRealName());
        createReq.setPhone(request.getPhone());
        createReq.setRoleCode("PARENT");

        User user = userService.createUser(createReq);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "PARENT", 0);
        List<String> permissions = loadPermissions("PARENT");
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(),
                "PARENT", permissions);
    }

    @Override
    public CurrentUserResponse profile(Long userId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .select(User::getId, User::getUsername, User::getRealName, User::getRoleCode)
                .eq(User::getId, userId));
        if (user == null) {
            throw new BusinessException(401, "登录状态无效，请重新登录");
        }
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode());
    }

    @Override
    public List<MenuTreeNode> getMyMenus(String roleCode) {
        // 1. 获取当前角色权限码集合
        List<String> permissions = loadPermissions(roleCode);
        Set<String> permSet = new HashSet<>(permissions);

        // 2. 查询所有可见菜单（按排序）
        List<Menu> allMenus = menuMapper.selectList(
                new LambdaQueryWrapper<Menu>()
                        .eq(Menu::getVisible, 1)
                        .orderByAsc(Menu::getSortOrder));

        // 3. 过滤：SUPER_ADMIN 可见全部；其余按权限码匹配
        boolean isSuperAdmin = "SUPER_ADMIN".equals(roleCode);
        List<Menu> visibleMenus = isSuperAdmin ? allMenus
                : allMenus.stream()
                        .filter(m -> m.getPermissionCode() == null || m.getPermissionCode().isBlank()
                                || permSet.contains(m.getPermissionCode()))
                        .collect(Collectors.toList());

        // 4. 构建 parentId → children 映射
        Map<Long, List<MenuTreeNode>> childrenMap = new HashMap<>();
        for (Menu m : visibleMenus) {
            Long pid = m.getParentId() == null || m.getParentId() == 0 ? 0L : m.getParentId();
            childrenMap.computeIfAbsent(pid, k -> new ArrayList<>())
                    .add(MenuTreeNode.builder()
                            .id(m.getId())
                            .parentId(m.getParentId())
                            .menuName(m.getMenuName())
                            .icon(m.getIcon())
                            .path(m.getPath())
                            .permissionCode(m.getPermissionCode())
                            .sortOrder(m.getSortOrder())
                            .visible(m.getVisible())
                            .build());
        }

        // 5. 递归构建树：从顶层(parentId=0)开始
        List<MenuTreeNode> tree = childrenMap.getOrDefault(0L, new ArrayList<>());
        for (MenuTreeNode node : tree) {
            buildChildren(node, childrenMap);
        }

        // 6. 过滤掉没有子节点的父节点组（无路由且无可见子菜单的空目录）
        return tree.stream()
                .filter(node -> node.getPath() != null && !node.getPath().isBlank()
                        || (node.getChildren() != null && !node.getChildren().isEmpty()))
                .collect(Collectors.toList());
    }

    /** 递归挂载子节点 */
    private void buildChildren(MenuTreeNode parent, Map<Long, List<MenuTreeNode>> childrenMap) {
        List<MenuTreeNode> children = childrenMap.get(parent.getId());
        if (children != null && !children.isEmpty()) {
            parent.setChildren(children);
            for (MenuTreeNode child : children) {
                buildChildren(child, childrenMap);
            }
        }
    }

    /** 根据 roleCode 查询角色拥有的权限码列表 */
    private List<String> loadPermissions(String roleCode) {
        try {
            return roleService.getRolePermissions(roleCode);
        } catch (Exception e) {
            log.error("权限加载失败(roleCode={})", roleCode, e);
            return Collections.emptyList();
        }
    }

    /**
     * Bug #37/#38: 清理过期的登录尝试记录，防止内存无限增长。
     * 移除超过锁定时长（15分钟）的条目，将内存限制为仅保留近期尝试。
     */
    private void cleanExpiredAttempts() {
        long now = System.currentTimeMillis();
        loginAttempts.entrySet().removeIf(entry ->
                now - entry.getValue().getLastAttemptTime() > LOCKOUT_DURATION_MS);
    }

    /** 记录登录失败，达到上限后锁定账号 */
    private void recordFailedAttempt(String username) {
        // Bug #37/#38: 每次记录前清理过期条目，防止内存无限增长
        cleanExpiredAttempts();
        loginAttempts.compute(username, (key, info) -> {
            if (info == null) {
                info = new LoginAttemptInfo();
            }
            info.increment();
            return info;
        });
    }

    /** 登录尝试信息内部类 */
    private static class LoginAttemptInfo {
        private final AtomicInteger count = new AtomicInteger(0);
        private volatile long lockTime = 0;
        private volatile long lastAttemptTime = System.currentTimeMillis();

        // H11 fix: synchronized 防止 isLocked/increment 并发竞态清除刚设置的锁
        synchronized void increment() {
            lastAttemptTime = System.currentTimeMillis();
            if (count.incrementAndGet() >= MAX_FAILED_ATTEMPTS) {
                lockTime = System.currentTimeMillis();
            }
        }

        long getLastAttemptTime() {
            return lastAttemptTime;
        }

        synchronized boolean isLocked() {
            if (lockTime == 0) return false;
            if (System.currentTimeMillis() - lockTime > LOCKOUT_DURATION_MS) {
                // 锁定过期，重置
                count.set(0);
                lockTime = 0;
                return false;
            }
            return true;
        }
    }
}
