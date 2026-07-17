package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.entity.Menu;
import com.pzhu.eduadmin.modules.user.entity.Permission;
import com.pzhu.eduadmin.modules.user.mapper.MenuMapper;
import com.pzhu.eduadmin.modules.user.mapper.PermissionMapper;
import com.pzhu.eduadmin.modules.user.service.MenuServiceImpl;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("菜单服务 Mock 单元测试")
class MenuServiceMockTest {

    @Mock private MenuMapper menuMapper;
    @Mock private PermissionMapper permissionMapper;
    @Mock private OperationLogMapper operationLogMapper;

    @InjectMocks
    private MenuServiceImpl menuService;

    private MockedStatic<IpUtil> ipUtilMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Menu.class);
        TableInfoHelper.initTableInfo(asst, Permission.class);
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
    @DisplayName("获取菜单树列表")
    void treeList_Success() {
        Menu m1 = new Menu();
        m1.setId(1L);
        m1.setMenuName("首页");
        m1.setParentId(0L);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(m1));

        List<Menu> result = menuService.treeList();

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("按 ID 查询菜单")
    void getById_Found() {
        Menu m = new Menu();
        m.setId(1L);
        m.setMenuName("首页");
        when(menuMapper.selectById(1L)).thenReturn(m);

        assertThat(menuService.getById(1L).getMenuName()).isEqualTo("首页");
    }

    @Test
    @DisplayName("查询不存在的菜单 — 抛出 404")
    void getById_NotFound() {
        when(menuMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> menuService.getById(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("菜单不存在");
    }

    @Test
    @DisplayName("创建菜单权限码不存在 — 抛出 400")
    void create_PermissionCodeNotFound() {
        Menu m = new Menu();
        m.setMenuName("新菜单");
        m.setPermissionCode("nonexistent");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

        assertThatThrownBy(() -> menuService.create(m))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("权限码不存在");
    }

    @Test
    @DisplayName("创建菜单成功 — 默认值填充并写日志")
    void create_Success() {
        Menu m = new Menu();
        m.setMenuName("新菜单");
        doAnswer(inv -> { inv.getArgument(0, Menu.class).setId(100L); return 1; })
                .when(menuMapper).insert(any(Menu.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Menu result = menuService.create(m);

        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getParentId()).isEqualTo(0L);
        assertThat(result.getSortOrder()).isEqualTo(0);
        assertThat(result.getVisible()).isEqualTo(1);
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("更新菜单权限码变更校验")
    void update_PermissionCodeChanged() {
        Menu existing = new Menu();
        existing.setId(10L);
        existing.setMenuName("旧菜单");
        existing.setPermissionCode("old:code");
        when(menuMapper.selectById(10L)).thenReturn(existing);

        Menu m = new Menu();
        m.setId(10L);
        m.setMenuName("新菜单");
        m.setPermissionCode("new:code");
        when(permissionMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        when(menuMapper.updateById(any(Menu.class))).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Menu result = menuService.update(m);

        assertThat(result.getMenuName()).isEqualTo("旧菜单");
    }

    @Test
    @DisplayName("删除菜单递归删除子节点并写日志")
    void delete_Success() {
        Menu m = new Menu();
        m.setId(1L);
        m.setMenuName("菜单A");
        when(menuMapper.selectById(1L)).thenReturn(m);

        // deleteChildren: first call returns children, second recursion returns empty
        Menu child = new Menu();
        child.setId(2L);
        when(menuMapper.selectList(any(LambdaQueryWrapper.class)))
                .thenReturn(List.of(child))
                .thenReturn(Collections.emptyList());
        when(menuMapper.deleteById(anyLong())).thenReturn(1);
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        menuService.delete(1L);

        verify(menuMapper, times(2)).deleteById(anyLong());
        verify(operationLogMapper).insert(any(OperationLog.class));
    }

    @Test
    @DisplayName("创建菜单无权限码 — 跳过校验")
    void create_NoPermissionCode() {
        Menu m = new Menu();
        m.setMenuName("无权限菜单");
        doAnswer(inv -> { inv.getArgument(0, Menu.class).setId(100L); return 1; })
                .when(menuMapper).insert(any(Menu.class));
        when(operationLogMapper.insert(any(OperationLog.class))).thenReturn(1);

        Menu result = menuService.create(m);

        assertThat(result.getId()).isEqualTo(100L);
        verify(permissionMapper, never()).selectCount(any());
    }
}
