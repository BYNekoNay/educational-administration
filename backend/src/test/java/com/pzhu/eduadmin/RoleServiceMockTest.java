package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.Role;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RoleMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.RoleServiceImpl;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("角色服务 Mock 单元测试")
class RoleServiceMockTest {

    @Mock private RoleMapper roleMapper;
    @Mock private PermissionMapper permissionMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private UserMapper userMapper;

    @InjectMocks
    private RoleServiceImpl roleService;

    private MockedStatic<IpUtil> ipUtilMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Role.class);
        TableInfoHelper.initTableInfo(asst, Permission.class);
        TableInfoHelper.initTableInfo(asst, RolePermission.class);
        TableInfoHelper.initTableInfo(asst, User.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
        ipUtilMock = mockStatic(IpUtil.class);
        ipUtilMock.when(IpUtil::getCurrentIp).thenReturn("127.0.0.1");
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
        if (ipUtilMock != null) ipUtilMock.close();
    }

    // ============ 查询 ============

    @Test
    @DisplayName("获取全部角色列表")
    void listRoles_Success() {
        Role r = new Role();
        r.setId(1L);
        r.setRoleCode("SUPER_ADMIN");
        when(roleMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r));

        assertThat(roleService.listRoles()).hasSize(1);
    }

    @Test
    @DisplayName("按 ID 查询角色")
    void getRoleById_Found() {
        Role r = buildRole(1L, "SUPER_ADMIN");
        when(roleMapper.selectById(1L)).thenReturn(r);

        assertThat(roleService.getRoleById(1L).getRoleCode()).isEqualTo("SUPER_ADMIN");
    }

    @Test
    @DisplayName("查询不存在的角色 — 抛出 404")
    void getRoleById_NotFound() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.getRoleById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    // ============ 创建 ============

    @Test
    @DisplayName("创建角色编码重复 — 抛出 400")
    void createRole_DuplicateCode() {
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> roleService.createRole("NEW", "新角色"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("创建角色成功并写日志")
    void createRole_Success() {
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> { inv.getArgument(0, Role.class).setId(100L); return 1; })
                .when(roleMapper).insert(any(Role.class));

        Role result = roleService.createRole("NEW", "新角色");

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getRoleCode()).isEqualTo("NEW");
    }

    // ============ 更新 ============

    @Test
    @DisplayName("更新系统内置角色 — 拒绝")
    void updateRole_SystemRole() {
        Role r = buildRole(1L, "SUPER_ADMIN");
        when(roleMapper.selectById(1L)).thenReturn(r);

        assertThatThrownBy(() -> roleService.updateRole(1L, "新名称"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可修改");
    }

    @Test
    @DisplayName("更新角色名称成功")
    void updateRole_Success() {
        Role r = buildRole(1L, "CUSTOM");
        when(roleMapper.selectById(1L)).thenReturn(r);
        when(roleMapper.updateById(any(Role.class))).thenReturn(1);

        roleService.updateRole(1L, "新名称");

        verify(roleMapper).updateById(any(Role.class));
    }

    // ============ 删除 ============

    @Test
    @DisplayName("删除系统内置角色 — 拒绝")
    void deleteRole_SystemRole() {
        Role r = buildRole(1L, "TEACHER");
        when(roleMapper.selectById(1L)).thenReturn(r);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可删除");
    }

    @Test
    @DisplayName("删除角色有关联用户 — 抛出 409")
    void deleteRole_HasUsers() {
        Role r = buildRole(1L, "CUSTOM");
        when(roleMapper.selectById(1L)).thenReturn(r);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(3L);

        assertThatThrownBy(() -> roleService.deleteRole(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仍有");
    }

    @Test
    @DisplayName("删除角色成功 — 物理删除并级联清权限")
    void deleteRole_Success() {
        Role r = buildRole(1L, "CUSTOM");
        when(roleMapper.selectById(1L)).thenReturn(r);
        when(userMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(rolePermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(roleMapper.realDeleteByRoleCode("CUSTOM")).thenReturn(1);


        roleService.deleteRole(1L);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        verify(roleMapper).realDeleteByRoleCode("CUSTOM");
    }

    @Test
    @DisplayName("删除不存在的角色 — 抛出 404")
    void deleteRole_NotFound() {
        when(roleMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> roleService.deleteRole(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("角色不存在");
    }

    // ============ 权限 ============

    @Test
    @DisplayName("获取角色权限码列表")
    void getRolePermissions_Success() {
        RolePermission rp = new RolePermission();
        rp.setRoleCode("SUPER_ADMIN");
        rp.setPermissionCode("menu:dashboard");
        when(rolePermissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(rp));

        List<String> result = roleService.getRolePermissions("SUPER_ADMIN");

        assertThat(result).containsExactly("menu:dashboard");
    }

    @Test
    @DisplayName("更新角色权限 — 先清后建")
    void updateRolePermissions_Success() {
        // M14: mock 角色存在性校验
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        // L7: mock 权限码存在性校验（2个权限码）
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(rolePermissionMapper.realDeleteByRoleCode("CUSTOM")).thenReturn(1);
        when(rolePermissionMapper.insert(any(RolePermission.class))).thenReturn(1);


        roleService.updateRolePermissions("CUSTOM", List.of("menu:a", "menu:b"));

        verify(rolePermissionMapper).realDeleteByRoleCode("CUSTOM");
        verify(rolePermissionMapper, times(2)).insert(any(RolePermission.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    @Test
    @DisplayName("更新角色权限为空列表 — 只删不建")
    void updateRolePermissions_EmptyList() {
        // M14: mock 角色存在性校验
        when(roleMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(rolePermissionMapper.realDeleteByRoleCode("CUSTOM")).thenReturn(1);


        roleService.updateRolePermissions("CUSTOM", List.of());

        verify(rolePermissionMapper).realDeleteByRoleCode("CUSTOM");
        verify(rolePermissionMapper, never()).insert(any(RolePermission.class));
    }

    private Role buildRole(Long id, String roleCode) {
        Role r = new Role();
        r.setId(id);
        r.setRoleCode(roleCode);
        r.setRoleName(roleCode + "_名称");
        return r;
    }
}
