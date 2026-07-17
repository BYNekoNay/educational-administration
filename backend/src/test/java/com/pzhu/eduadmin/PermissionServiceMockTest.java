package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.entity.RolePermission;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.mapper.RolePermissionMapper;
import com.pzhu.eduadmin.modules.user.service.PermissionServiceImpl;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("权限服务 Mock 单元测试")
class PermissionServiceMockTest {

    @Mock private PermissionMapper permissionMapper;
    @Mock private RolePermissionMapper rolePermissionMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @InjectMocks
    private PermissionServiceImpl permissionService;

    private MockedStatic<IpUtil> ipUtilMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Permission.class);
        TableInfoHelper.initTableInfo(asst, RolePermission.class);
        TableInfoHelper.initTableInfo(asst, OperationLog.class);
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

    @Test
    @DisplayName("获取全部权限列表")
    void listAll_Success() {
        Permission p = new Permission();
        p.setId(1L);
        p.setPermissionCode("menu:dashboard");
        when(permissionMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(p));

        List<Permission> result = permissionService.listAll();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("按 ID 查询权限")
    void getById_Found() {
        Permission p = new Permission();
        p.setId(1L);
        p.setPermissionCode("menu:dashboard");
        when(permissionMapper.selectById(1L)).thenReturn(p);

        assertThat(permissionService.getById(1L).getPermissionCode()).isEqualTo("menu:dashboard");
    }

    @Test
    @DisplayName("查询不存在的权限 — 抛出 404")
    void getById_NotFound() {
        when(permissionMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> permissionService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限不存在");
    }

    @Test
    @DisplayName("创建权限码重复 — 抛出 400")
    void create_DuplicateCode() {
        Permission p = new Permission();
        p.setPermissionCode("menu:dashboard");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> permissionService.create(p))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限码已存在");
    }

    @Test
    @DisplayName("创建权限成功并写日志")
    void create_Success() {
        Permission p = new Permission();
        p.setPermissionCode("menu:new");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        doAnswer(inv -> { inv.getArgument(0, Permission.class).setId(100L); return 1; })
                .when(permissionMapper).insert(any(Permission.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Permission result = permissionService.create(p);

        assertThat(result.getId()).isEqualTo(100L);
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("更新权限码变更不重复")
    void update_CodeChangedNotDuplicate() {
        Permission existing = new Permission();
        existing.setId(10L);
        existing.setPermissionCode("old:code");
        when(permissionMapper.selectById(10L)).thenReturn(existing);

        Permission p = new Permission();
        p.setId(10L);
        p.setPermissionCode("new:code");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);
        when(permissionMapper.updateById(any(Permission.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Permission result = permissionService.update(p);

        assertThat(result.getPermissionCode()).isEqualTo("new:code");
    }

    @Test
    @DisplayName("更新权限码变更为已存在码 — 抛出 400")
    void update_CodeChangedDuplicate() {
        Permission existing = new Permission();
        existing.setId(10L);
        existing.setPermissionCode("old:code");
        when(permissionMapper.selectById(10L)).thenReturn(existing);

        Permission p = new Permission();
        p.setId(10L);
        p.setPermissionCode("taken:code");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> permissionService.update(p))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限码已存在");
    }

    @Test
    @DisplayName("删除权限级联清除关联并写日志")
    void delete_Success() {
        Permission p = new Permission();
        p.setId(1L);
        p.setPermissionCode("menu:delete");
        when(permissionMapper.selectById(1L)).thenReturn(p);
        when(rolePermissionMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);
        when(permissionMapper.deleteById(1L)).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        permissionService.delete(1L);

        verify(rolePermissionMapper).delete(any(LambdaQueryWrapper.class));
        verify(permissionMapper).deleteById(1L);
    }
}
