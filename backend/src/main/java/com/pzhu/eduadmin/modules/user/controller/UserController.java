package com.pzhu.eduadmin.modules.user.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.user.dto.CreateUserRequest;
import com.pzhu.eduadmin.modules.user.dto.UpdateUserRequest;
import com.pzhu.eduadmin.modules.user.entity.User;
import com.pzhu.eduadmin.modules.user.service.UserService;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 用户增删改查、启用禁用。对应 docs/11-后端开发详细文档.md §2。
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@RequireRole({"SUPER_ADMIN"})
public class UserController {

    private final UserService userService;

    @GetMapping
    public Result<PageResult<User>> list(PageQuery query) {
        return Result.success(PageResult.of(userService.pageUsers(
                (int) query.getPageNum(), (int) query.getPageSize(), query.getKeyword(),
                query.getSortField(), query.getSortOrder())));
    }

    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        userService.updateUserStatus(id, status);
        return Result.success();
    }

    @PostMapping
    public Result<User> create(@Valid @RequestBody CreateUserRequest request) {
        return Result.success(userService.createUser(request));
    }

    @PutMapping("/{id}")
    public Result<User> update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
        return Result.success(userService.updateUser(id, request));
    }

    @PutMapping("/{id}/password")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody java.util.Map<String, String> body) {
        userService.resetPassword(id, body.get("newPassword"));
        return Result.success();
    }
}
