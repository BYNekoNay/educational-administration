package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.auth.service.AuthService;
import com.pzhu.eduadmin.modules.user.dto.*;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.RoleService;
import com.pzhu.eduadmin.modules.user.service.UserService;
import com.pzhu.eduadmin.security.JwtUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.lang.reflect.Field;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("登录鉴权服务 Mock 单元测试")
class AuthServiceMockTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private UserService userService;
    @Mock
    private RoleService roleService;

    @InjectMocks
    private AuthService authService;

    private static final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private static final String RAW_PASSWORD = "password123";
    private String encodedPassword;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, User.class);
    }

    @BeforeEach
    void setUp() {
        encodedPassword = encoder.encode(RAW_PASSWORD);
    }

    private User buildUser(Long id, String username, String roleCode, Integer status, Integer version) {
        User user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setPassword(encodedPassword);
        user.setRealName("测试用户");
        user.setRoleCode(roleCode);
        user.setStatus(status);
        user.setVersion(version);
        return user;
    }

    // ==================== login ====================

    @Test
    @DisplayName("正常登录 — 密码匹配、状态正常、返回 token 与权限")
    void login_Success() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(jwtUtil.generateToken(1L, "admin", "SUPER_ADMIN", 0)).thenReturn("mock-jwt-token");
        when(roleService.getRolePermissions("SUPER_ADMIN")).thenReturn(List.of("menu:dashboard", "menu:user"));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("mock-jwt-token");
        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getRealName()).isEqualTo("测试用户");
        assertThat(response.getRoleCode()).isEqualTo("SUPER_ADMIN");
        assertThat(response.getPermissions()).containsExactly("menu:dashboard", "menu:user");
        verify(userMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("用户名不存在 — 抛出用户名或密码错误")
    void login_UserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("nobody");
        request.setPassword(RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("密码错误 — 抛出用户名或密码错误")
    void login_WrongPassword() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrongpassword");

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("账号已禁用(status=0) — 抛出账号已被禁用")
    void login_DisabledAccount() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 0, 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("status 为 null — 视为禁用")
    void login_StatusNull() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", null, 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("暴力破解防护 — 连续5次登录失败后第6次被锁定")
    void login_BruteForceLockout() {
        when(userMapper.selectOne(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("账号已锁定");
    }

    @Test
    @DisplayName("锁定过期(>15分钟)后自动解锁可重试")
    @SuppressWarnings("unchecked")
    void login_LockExpiry() throws Exception {
        when(userMapper.selectOne(any())).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong");

        // 锁定
        for (int i = 0; i < 5; i++) {
            try { authService.login(request); } catch (BusinessException ignored) {}
        }

        // 反射设置锁的时间为 16 分钟前（超过 15 分钟锁定窗口）
        Field attemptsField = AuthService.class.getDeclaredField("loginAttempts");
        attemptsField.setAccessible(true);
        ConcurrentHashMap<String, Object> attempts = (ConcurrentHashMap<String, Object>) attemptsField.get(authService);
        Object info = attempts.get("admin");
        Field lockTimeField = info.getClass().getDeclaredField("lockTime");
        lockTimeField.setAccessible(true);
        lockTimeField.setLong(info, System.currentTimeMillis() - 16 * 60 * 1000);

        // 现在应该可以登录了（锁定已过期）
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyInt())).thenReturn("token");
        when(roleService.getRolePermissions("SUPER_ADMIN")).thenReturn(List.of());

        LoginRequest okReq = new LoginRequest();
        okReq.setUsername("admin");
        okReq.setPassword(RAW_PASSWORD);
        LoginResponse response = authService.login(okReq);

        assertThat(response.getToken()).isEqualTo("token");
    }

    @Test
    @DisplayName("登录成功后清除失败计数 — 之后可正常登录")
    void login_ResetFailedCountAfterSuccess() {
        User admin = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);

        // 先失败 3 次
        when(userMapper.selectOne(any())).thenReturn(null);
        LoginRequest failReq = new LoginRequest();
        failReq.setUsername("admin");
        failReq.setPassword("wrong");
        for (int i = 0; i < 3; i++) {
            try { authService.login(failReq); } catch (BusinessException ignored) {}
        }

        // 成功登录一次 — 计数器清零
        when(userMapper.selectOne(any())).thenReturn(admin);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyInt())).thenReturn("token");
        when(roleService.getRolePermissions(anyString())).thenReturn(List.of());

        LoginRequest okReq = new LoginRequest();
        okReq.setUsername("admin");
        okReq.setPassword(RAW_PASSWORD);
        authService.login(okReq);

        // 再失败 — 从 0 开始重新计数
        when(userMapper.selectOne(any())).thenReturn(null);
        for (int i = 0; i < 4; i++) {
            try { authService.login(failReq); } catch (BusinessException ignored) {}
        }
        // 第 5 次仍是"密码错误"（锁在第 5 次 increment 中设置，第 6 次才触发检测）
        assertThatThrownBy(() -> authService.login(failReq))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名或密码错误");
    }

    @Test
    @DisplayName("version 为 null 时默认使用 0 签发 token")
    void login_VersionNullDefaultZero() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, null);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(roleService.getRolePermissions("SUPER_ADMIN")).thenReturn(List.of());

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        authService.login(request);

        verify(jwtUtil).generateToken(eq(1L), eq("admin"), eq("SUPER_ADMIN"), eq(0));
    }

    @Test
    @DisplayName("登录成功后更新 lastLoginTime")
    void login_UpdateLastLoginTime() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyInt())).thenReturn("token");
        when(roleService.getRolePermissions("SUPER_ADMIN")).thenReturn(List.of());

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        authService.login(request);

        // H1 fix 后使用 LambdaUpdateWrapper 仅更新 lastLoginTime，验证 update 被调用
        verify(userMapper).update(any(), any(LambdaUpdateWrapper.class));
    }

    @Test
    @DisplayName("权限加载失败时返回空列表不阻断登录")
    void login_PermissionsLoadFailedReturnsEmpty() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any(LambdaUpdateWrapper.class))).thenReturn(1);
        when(jwtUtil.generateToken(anyLong(), anyString(), anyString(), anyInt())).thenReturn("token");
        when(roleService.getRolePermissions("SUPER_ADMIN")).thenThrow(new RuntimeException("DB error"));

        LoginRequest request = new LoginRequest();
        request.setUsername("admin");
        request.setPassword(RAW_PASSWORD);

        LoginResponse response = authService.login(request);

        assertThat(response.getToken()).isEqualTo("token");
        assertThat(response.getPermissions()).isEmpty();
    }

    // ==================== register ====================

    @Test
    @DisplayName("正常注册 — 角色固定为 PARENT，返回 token")
    void register_Success() {
        User user = buildUser(2L, "newparent", "PARENT", 1, 0);
        when(userService.createUser(any(CreateUserRequest.class))).thenReturn(user);
        when(jwtUtil.generateToken(2L, "newparent", "PARENT", 0)).thenReturn("reg-token");
        when(roleService.getRolePermissions("PARENT")).thenReturn(List.of("menu:dashboard"));

        RegisterRequest request = new RegisterRequest();
        request.setUsername("newparent");
        request.setPassword("pass123456");
        request.setRealName("新家长");
        request.setPhone("13800138000");

        LoginResponse response = authService.register(request);

        assertThat(response.getToken()).isEqualTo("reg-token");
        assertThat(response.getUserId()).isEqualTo(2L);
        assertThat(response.getRoleCode()).isEqualTo("PARENT");
        assertThat(response.getPermissions()).containsExactly("menu:dashboard");

        ArgumentCaptor<CreateUserRequest> captor = ArgumentCaptor.forClass(CreateUserRequest.class);
        verify(userService).createUser(captor.capture());
        assertThat(captor.getValue().getRoleCode()).isEqualTo("PARENT");
    }

    // ==================== profile ====================

    @Test
    @DisplayName("正常获取用户信息")
    void profile_Success() {
        User user = buildUser(1L, "admin", "SUPER_ADMIN", 1, 0);
        when(userMapper.selectOne(any())).thenReturn(user);

        CurrentUserResponse response = authService.profile(1L);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(response.getUsername()).isEqualTo("admin");
        assertThat(response.getRealName()).isEqualTo("测试用户");
        assertThat(response.getRoleCode()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("用户不存在 — 抛出 401")
    void profile_UserNotFound() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> authService.profile(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("登录状态无效");
    }
}
