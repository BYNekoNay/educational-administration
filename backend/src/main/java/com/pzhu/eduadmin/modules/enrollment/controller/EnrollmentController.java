package com.pzhu.eduadmin.modules.enrollment.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.enrollment.service.EnrollmentService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.LoginUser;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/edu/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    @GetMapping
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<PageResult<Enrollment>> list(PageQuery query) {
        return Result.success(PageResult.of(enrollmentService.page((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Enrollment> get(@PathVariable Long id) {
        return Result.success(enrollmentService.getById(id));
    }

    @PostMapping
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "PARENT"})
    public Result<Enrollment> create(@Valid @RequestBody Enrollment enrollment) {
        // 家长报名时自动填充 parentUserId
        if (enrollment.getParentUserId() == null) {
            LoginUser loginUser = CurrentUserHolder.get();
            enrollment.setParentUserId(loginUser.getUserId());
        }
        enrollment.setStatus(1); // 待审核
        return Result.success(enrollmentService.create(enrollment));
    }

    @PutMapping("/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Enrollment> update(@PathVariable Long id, @RequestBody Enrollment enrollment) {
        enrollment.setId(id);
        return Result.success(enrollmentService.update(enrollment));
    }

    @DeleteMapping("/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Void> delete(@PathVariable Long id) {
        enrollmentService.delete(id);
        return Result.success();
    }

    @PutMapping("/{id}/audit")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Enrollment> audit(@PathVariable Long id, @RequestParam Integer status, @RequestParam(required = false) String remark) {
        LoginUser loginUser = CurrentUserHolder.get();
        return Result.success(enrollmentService.audit(id, status, loginUser.getUserId(), remark));
    }
}
