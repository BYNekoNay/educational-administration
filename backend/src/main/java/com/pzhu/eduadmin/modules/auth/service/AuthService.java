package com.pzhu.eduadmin.modules.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.CurrentUserResponse;
import com.pzhu.eduadmin.modules.user.dto.LoginRequest;
import com.pzhu.eduadmin.modules.user.dto.LoginResponse;
import com.pzhu.eduadmin.modules.user.dto.RegisterRequest;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.modules.user.service.UserService;
import com.pzhu.eduadmin.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 登录鉴权服务实现：密码校验、Token 签发、用户信息查询。
 * 对应 docs/11-后端开发详细文档.md §2、docs/08-开发指南.md §2.2 分层约定。
 */
@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final UserService userService;
    private final com.pzhu.eduadmin.modules.user.service.RoleService roleService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, request.getUsername()));

        if (user == null) {
            throw new BusinessException("用户名不存在，请检查用户名");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("密码错误，请重新输入");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }

        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), user.getRoleCode());
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

        String token = jwtUtil.generateToken(user.getId(), user.getUsername(), "PARENT");
        List<String> permissions = loadPermissions("PARENT");
        return new LoginResponse(token, user.getId(), user.getUsername(), user.getRealName(),
                "PARENT", permissions);
    }

    @Override
    public CurrentUserResponse profile(Long userId) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getId, userId));
        if (user == null) {
            throw new BusinessException(401, "登录状态无效，请重新登录");
        }
        return new CurrentUserResponse(user.getId(), user.getUsername(), user.getRealName(), user.getRoleCode());
    }

    /** 根据 roleCode 查询角色拥有的权限码列表 */
    private List<String> loadPermissions(String roleCode) {
        try {
            return roleService.getRolePermissions(roleCode);
        } catch (Exception e) {
            // 权限加载失败不影响登录流程，返回空列表（至少可访问看板）
            return Collections.emptyList();
        }
    }
}
