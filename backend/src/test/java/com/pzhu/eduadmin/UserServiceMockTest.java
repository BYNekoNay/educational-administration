package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.IpUtil;
import com.pzhu.eduadmin.common.QueryHelper;
import com.pzhu.eduadmin.modules.course.mapper.CourseMapper;
import com.pzhu.eduadmin.common.EntityNameResolver;
import com.pzhu.eduadmin.modules.statistics.service.OperationLogService;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.TeacherCourse;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.RoleMapper;
import com.pzhu.eduadmin.modules.user.mapper.TeacherCourseMapper;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.UserServiceImpl;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("用户服务 Mock 单元测试")
class UserServiceMockTest {

    @Mock private UserMapper userMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private OperationLogService operationLogService;
    @Mock private EntityNameResolver nameResolver;
    @Mock private TeacherCourseMapper teacherCourseMapper;
    @Mock private CourseMapper courseMapper;

    @InjectMocks
    private UserServiceImpl userService;

    private MockedStatic<QueryHelper> queryHelperMock;
    private MockedStatic<IpUtil> ipUtilMock;

    @BeforeAll
    static void initLambdaCache() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), User.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), TeacherCourse.class);
    }

    @BeforeEach
    void setUp() {
        CurrentUserHolder.set(new LoginUser(1L, "admin", "SUPER_ADMIN"));
        queryHelperMock = mockStatic(QueryHelper.class);
        ipUtilMock = mockStatic(IpUtil.class);
        ipUtilMock.when(IpUtil::getCurrentIp).thenReturn("127.0.0.1");
        lenient().when(nameResolver.getUserDisplayName(anyLong())).thenReturn("测试用户");
        // Bug #34: roleMapper 校验角色编码存在性
        lenient().when(roleMapper.selectCount(any())).thenReturn(1L);
        // 默认让 teacherCourseMapper.selectList 返回空列表，避免 fillUserSpecialties 中 NPE
        lenient().when(teacherCourseMapper.selectList(any())).thenReturn(Collections.emptyList());
        lenient().when(teacherCourseMapper.realDeleteByUserId(any())).thenReturn(0);
        lenient().when(teacherCourseMapper.insert(any(TeacherCourse.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        CurrentUserHolder.clear();
        queryHelperMock.close();
        ipUtilMock.close();
    }

    // ========== 辅助方法 ==========

    private CreateUserRequest buildCreateRequest() {
        CreateUserRequest req = new CreateUserRequest();
        req.setUsername("testuser");
        req.setPassword("123456");
        req.setRealName("测试用户");
        req.setPhone("13800138000");
        req.setRoleCode("STUDENT");
        return req;
    }

    private UpdateUserRequest buildUpdateRequest() {
        UpdateUserRequest req = new UpdateUserRequest();
        req.setUsername("updated");
        req.setRealName("更新姓名");
        req.setPhone("13900139000");
        req.setRoleCode("STUDENT");
        return req;
    }

    private User buildExistingUser(Long id, String roleCode, int version) {
        User user = new User();
        user.setId(id);
        user.setUsername("olduser");
        user.setPassword("$2a$10$hashedpassword");
        user.setRealName("旧姓名");
        user.setPhone("13700137000");
        user.setRoleCode(roleCode);
        user.setStatus(1);
        user.setVersion(version);
        return user;
    }

    // ========== 1. createUser() — 正常创建用户成功 ==========

    @Test
    @DisplayName("createUser — 正常创建用户成功")
    void createUser_normalSuccess() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenReturn(1);

        CreateUserRequest req = buildCreateRequest();
        User result = userService.createUser(req);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("testuser");
        assertThat(result.getRealName()).isEqualTo("测试用户");
        assertThat(result.getRoleCode()).isEqualTo("STUDENT");
        assertThat(result.getStatus()).isEqualTo(1);
        // 返回的密码应被清除
        assertThat(result.getPassword()).isNull();

        verify(userMapper).insert(any(User.class));
        verify(operationLogService).log(anyString(), anyString());
    }

    // ========== 2. createUser() — 用户名已存在应拒绝 ==========

    @Test
    @DisplayName("createUser — 用户名已存在应拒绝")
    void createUser_duplicateUsername() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(new User());

        assertThatThrownBy(() -> userService.createUser(buildCreateRequest()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");

        verify(userMapper, never()).insert(any(User.class));
    }

    // ========== 3. updateUser() — 正常更新成功 ==========

    @Test
    @DisplayName("updateUser — 正常更新成功")
    void updateUser_normalSuccess() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateUserRequest req = buildUpdateRequest();
        User result = userService.updateUser(1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("updated");
        assertThat(result.getRealName()).isEqualTo("更新姓名");
        assertThat(result.getPhone()).isEqualTo("13900139000");
        // 返回的密码应被清除
        assertThat(result.getPassword()).isNull();

        verify(userMapper).updateById(any(User.class));
    }

    // ========== 3b. updateUser() — 用户名与其他用户重复应拒绝 ==========

    @Test
    @DisplayName("updateUser — 用户名与其他用户重复应拒绝")
    void updateUser_duplicateUsername_rejected() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);

        User anotherUser = new User();
        anotherUser.setId(2L);
        anotherUser.setUsername("updated");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(anotherUser);

        UpdateUserRequest req = buildUpdateRequest();
        req.setUsername("updated"); // same as anotherUser's username

        assertThatThrownBy(() -> userService.updateUser(1L, req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户名已存在");

        verify(userMapper, never()).updateById(any(User.class));
    }

    // ========== 4. updateUser() — 角色变更时应递增 version ==========

    @Test
    @DisplayName("updateUser — 角色变更时应递增 version")
    void updateUser_roleChanged_incrementsVersion() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.updateById(any(User.class))).thenReturn(1);
        // C5 fix: version 通过单独的原子 SQL 递增
        when(userMapper.update(any(), any())).thenReturn(1);

        UpdateUserRequest req = buildUpdateRequest();
        req.setRoleCode("TEACHER");
        req.setSpecialtyCourseIds(List.of(10L, 20L));

        User result = userService.updateUser(1L, req);

        assertThat(result).isNotNull();
        assertThat(result.getRoleCode()).isEqualTo("TEACHER");
        // C5 fix: version 通过原子 SQL 递增，验证 update(null, wrapper) 被调用
        verify(userMapper).update(any(), any());
    }

    // ========== 5. updateUser() — 角色不变时不递增 version ==========

    @Test
    @DisplayName("updateUser — 角色不变时不递增 version")
    void updateUser_roleNotChanged_noVersionIncrement() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        when(userMapper.updateById(any(User.class))).thenReturn(1);

        UpdateUserRequest req = buildUpdateRequest();
        req.setRoleCode("STUDENT"); // 与原角色相同
        req.setUsername("olduser"); // 与原用户名相同，不触发 version 递增

        User result = userService.updateUser(1L, req);

        assertThat(result).isNotNull();
        // version 保持不变
        assertThat(result.getVersion()).isEqualTo(1);
    }

    // ========== 6. updateUserStatus() — 禁用用户应递增 version ==========

    @Test
    @DisplayName("updateUserStatus — 禁用用户应递增 version")
    void updateUserStatus_disableUser_incrementsVersion() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        // C5 fix: 现在使用 update(null, wrapper) 原子递增 version
        when(userMapper.update(any(), any())).thenReturn(1);

        userService.updateUserStatus(1L, 0);

        // C5 fix: version 通过原子 SQL 递增，不再修改 Java 对象
        verify(userMapper).update(any(), any());
        verify(operationLogService).log(anyString(), anyString());
    }

    // ========== 7. resetPassword() — 正常重置密码应递增 version ==========

    @Test
    @DisplayName("resetPassword — 正常重置密码应递增 version")
    void resetPassword_normalSuccess_incrementsVersion() {
        User existing = buildExistingUser(1L, "STUDENT", 1);
        when(userMapper.selectById(1L)).thenReturn(existing);
        // C5 fix: 现在使用 update(null, wrapper) 原子递增 version
        when(userMapper.update(any(), any())).thenReturn(1);

        userService.resetPassword(1L, "newpass123");

        // C5 fix: 密码和 version 通过原子 SQL 更新，不再修改 Java 对象
        verify(userMapper).update(any(), any());
        verify(operationLogService).log(anyString(), anyString());
    }

    // ========== 8. resetPassword() — 密码为空应拒绝 ==========

    @Test
    @DisplayName("resetPassword — 密码为空应拒绝")
    void resetPassword_emptyPassword_rejected() {
        assertThatThrownBy(() -> userService.resetPassword(1L, ""))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能为空");

        verify(userMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("resetPassword — 密码为 null 应拒绝")
    void resetPassword_nullPassword_rejected() {
        assertThatThrownBy(() -> userService.resetPassword(1L, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码不能为空");

        verify(userMapper, never()).selectById(any());
    }

    // ========== 9. resetPassword() — 密码少于6位应拒绝 ==========

    @Test
    @DisplayName("resetPassword — 密码少于6位应拒绝")
    void resetPassword_shortPassword_rejected() {
        assertThatThrownBy(() -> userService.resetPassword(1L, "12345"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("新密码长度不能少于6位");

        verify(userMapper, never()).selectById(any());
    }

    // ========== 10. resetPassword() — 用户不存在应拒绝 ==========

    @Test
    @DisplayName("resetPassword — 用户不存在应拒绝")
    void resetPassword_userNotFound_rejected() {
        when(userMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> userService.resetPassword(999L, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("用户不存在");

        verify(userMapper, never()).updateById(any(User.class));
    }

    // ========== 11. pageUsers() — 密码应被清除（不返回给前端） ==========

    @Test
    @DisplayName("pageUsers — 密码应被清除不返回给前端")
    void pageUsers_passwordsShouldBeCleared() {
        User teacher = buildExistingUser(1L, "TEACHER", 1);
        teacher.setPassword("hashed_pw_1");
        User student = buildExistingUser(2L, "STUDENT", 0);
        student.setPassword("hashed_pw_2");

        Page<User> mockPage = new Page<>(1, 10);
        mockPage.setRecords(List.of(teacher, student));
        when(userMapper.selectPage(any(Page.class), any())).thenReturn(mockPage);

        Page<User> result = userService.pageUsers(1, 10, null, null, null);

        assertThat(result.getRecords()).hasSize(2);
        assertThat(result.getRecords()).allSatisfy(u ->
                assertThat(u.getPassword()).isNull()
        );
    }
}
