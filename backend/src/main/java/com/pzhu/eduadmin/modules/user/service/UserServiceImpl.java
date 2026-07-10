package com.pzhu.eduadmin.modules.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.statistics.entity.OperationLog;
import com.pzhu.eduadmin.modules.statistics.mapper.OperationLogMapper;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.mapper.UserMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final OperationLogMapper operationLogMapper;

    @Override
    public Page<User> pageUsers(int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w
                    .like(User::getUsername, keyword)
                    .or()
                    .like(User::getRealName, keyword));
        }
        wrapper.orderByDesc(User::getCreateTime);
        return userMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    @Override
    public User getUserById(Long id) {
        return userMapper.selectById(id);
    }

    @Override
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

        logOperation("用户管理", "新增用户(username=" + user.getUsername() + ")");

        user.setPassword(null);
        return user;
    }

    @Override
    public User updateUser(Long id, UpdateUserRequest request) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (request.getUsername() != null) user.setUsername(request.getUsername());
        if (request.getRealName() != null) user.setRealName(request.getRealName());
        if (request.getPhone() != null) user.setPhone(request.getPhone());
        if (request.getRoleCode() != null) user.setRoleCode(request.getRoleCode());
        userMapper.updateById(user);

        logOperation("用户管理", "编辑用户(id=" + id + ")");

        user.setPassword(null);
        return user;
    }

    @Override
    public void updateUserStatus(Long id, Integer status) {
        User user = new User();
        user.setId(id);
        user.setStatus(status);
        userMapper.updateById(user);

        logOperation("用户管理", status == 1 ? "启用用户(id=" + id + ")" : "禁用用户(id=" + id + ")");
    }

    private void logOperation(String module, String operation) {
        OperationLog log = new OperationLog();
        log.setOperatorId(CurrentUserHolder.get().getUserId());
        log.setModule(module);
        log.setOperation(operation);
        log.setIp("0.0.0.0");
        operationLogMapper.insert(log);
    }
}
