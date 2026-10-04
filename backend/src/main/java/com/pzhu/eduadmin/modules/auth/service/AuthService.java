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
import com.pzhu.eduadmin.security.LoginAttemptService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.*;
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

    /**
     * 登录失败计数（SEC-03）。原先在本类用进程内 ConcurrentHashMap 按用户名计数，
     * 存在两点缺陷：多副本部署时计数不共享使爆破防护可被绕过；仅按用户名维度锁定
     * 会被反向利用为锁定他人账号的 DoS。已下沉到 LoginAttemptService（Redis 分布式 +
     * 账号/IP 双维度）。
     */
    private final LoginAttemptService loginAttemptService;

    @Override
    public LoginResponse login(LoginRequest request) {
        String username = request.getUsername();
        // 计数键在 LoginAttemptService 内做 trim+lowercase 归一化。DB 用大小写/尾空格不敏感的排序规则，
        // admin/Admin/"admin " 解析到同一用户，若按原始串计数会各自独立计数，攻击者可借大小写/空格变体
        // 成倍扩大暴力破解额度（DB 查询仍用原始 username）。
        String clientIp = resolveClientIp();

        // 暴力破解防护：账号维度与来源 IP 维度任一超限即拒绝
        loginAttemptService.checkLocked(username, clientIp);

        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username));

        if (user == null) {
            loginAttemptService.recordFailure(username, clientIp);
            throw new BusinessException("用户名或密码错误");
        }
        // 先检查账号状态再验证密码，防止攻击者确认禁用账号的密码
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("用户名或密码错误");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.recordFailure(username, clientIp);
            throw new BusinessException("用户名或密码错误");
        }

        // 登录成功，清除失败计数
        loginAttemptService.clearSuccess(username, clientIp);

        // 仅更新 lastLoginTime，避免 updateById 将 stale status/version 写回覆盖并发操作
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
        List<Menu> visibleMenus;
        if (isSuperAdmin) {
            visibleMenus = allMenus;
        } else {
            Map<Long, Menu> menuById = allMenus.stream()
                    .collect(Collectors.toMap(Menu::getId, m -> m, (a, b) -> a));
            Set<Long> visibleIds = allMenus.stream()
                    .filter(m -> m.getPermissionCode() == null || m.getPermissionCode().isBlank()
                            || permSet.contains(m.getPermissionCode()))
                    .map(Menu::getId)
                    .collect(Collectors.toSet());
            // 目录节点即使自身权限码未授予，只要含可见子菜单也必须保留，
            // 否则子菜单挂在被过滤掉的父节点下，从根(parentId=0)不可达而整体消失（如 TEACHER 丢失排课/教室/考勤菜单）
            Set<Long> withAncestors = new HashSet<>(visibleIds);
            for (Long vid : visibleIds) {
                Menu node = menuById.get(vid);
                Long pid = node == null ? null : node.getParentId();
                Set<Long> guard = new HashSet<>();
                while (pid != null && pid != 0L && guard.add(pid)) {
                    withAncestors.add(pid);
                    Menu p = menuById.get(pid);
                    pid = p == null ? null : p.getParentId();
                }
            }
            visibleMenus = allMenus.stream()
                    .filter(m -> withAncestors.contains(m.getId()))
                    .collect(Collectors.toList());
        }

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
     * 解析来源 IP，供限流的 IP 维度使用。
     *
     * <p>取值顺序：nginx 覆盖式写入的 X-Forwarded-For 首段 → X-Real-IP → TCP 远端地址。
     * 之所以信任 XFF 首段，是因为 nginx 配置用的是 {@code X-Forwarded-For $remote_addr}
     * 而非默认的追加式 {@code $proxy_add_x_forwarded_for}，客户端伪造的前缀不会留存；
     * 而容器内 getRemoteAddr() 恒为 nginx 地址，直接取会让所有来源共享同一计数。</p>
     *
     * <p>非 Web 上下文（单元测试、定时任务）返回 null，此时 IP 维度不参与判定。</p>
     */
    private String resolveClientIp() {
        try {
            if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                String xff = request.getHeader("X-Forwarded-For");
                if (xff != null && !xff.isBlank()) {
                    String first = xff.split(",")[0].trim();
                    if (!first.isEmpty()) {
                        return first;
                    }
                }
                String realIp = request.getHeader("X-Real-IP");
                if (realIp != null && !realIp.isBlank()) {
                    return realIp.trim();
                }
                return request.getRemoteAddr();
            }
        } catch (Exception e) {
            log.warn("来源 IP 解析失败，本次登录仅按账号维度限流", e);
        }
        return null;
    }
}
