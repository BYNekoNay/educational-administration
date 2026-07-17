package com.pzhu.eduadmin.modules.enrollment.controller;

import com.pzhu.eduadmin.common.BusinessException;
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
        Enrollment e = enrollmentService.getById(id);
        if (e == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        return Result.success(e);
    }

    @PostMapping
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN", "PARENT"})
    public Result<Enrollment> create(@Valid @RequestBody Enrollment enrollment) {
        LoginUser loginUser = CurrentUserHolder.get();
        // 家长角色强制绑定 parentUserId，防止伪造其他家长身份
        if ("PARENT".equals(loginUser.getRoleCode())) {
            enrollment.setParentUserId(loginUser.getUserId());
        } else if (enrollment.getParentUserId() == null) {
            enrollment.setParentUserId(loginUser.getUserId());
        }
        enrollment.setStatus(1); // 待审核
        return Result.success(enrollmentService.create(enrollment));
    }

    @PutMapping("/{id}")
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Enrollment> update(@PathVariable Long id, @RequestBody Enrollment enrollment) {
        Enrollment existing = enrollmentService.getById(id);
        if (existing == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        // M2: classId 变更保护
        if (enrollment.getClassId() != null && !enrollment.getClassId().equals(existing.getClassId())) {
            if (existing.getStatus() == 3) {
                throw new BusinessException(409, "已缴费的报名不可变更班级");
            }
        }
        // 仅允许更新安全字段，防止通过 update 接口篡改 status / auditorId 等字段
        if (enrollment.getClassId() != null) {
            existing.setClassId(enrollment.getClassId());
        }
        return Result.success(enrollmentService.update(existing));
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
