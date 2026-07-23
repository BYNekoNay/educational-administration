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
    @RequireRole({"SUPER_ADMIN", "EDU_ADMIN"})
    public Result<Enrollment> create(@Valid @RequestBody Enrollment enrollment) {
        // H4 fix: 清除客户端不应设置的服务端控制字段
        enrollment.setId(null);
        enrollment.setAuditorId(null);
        enrollment.setAuditRemark(null);
        enrollment.setHoldExpireTime(null);
        enrollment.setIsDeleted(null);
        enrollment.setCreateTime(null);
        enrollment.setUpdateTime(null);
        LoginUser loginUser = CurrentUserHolder.get();
        if (enrollment.getParentUserId() == null) {
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
            // A2#1 fix: 仅待审核(1)/待缴费(2)可变更班级；终态(>=3 已缴费/已拒绝/已失效/已退费)禁止，
            // 否则改班级会污染按班级归属的统计，且 null 状态直接拆箱会 NPE
            Integer st = existing.getStatus();
            if (st == null || st >= 3) {
                throw new BusinessException(409, "当前报名状态不可变更班级");
            }
            // M4 fix: 校验新班级归属于该课程
            enrollmentService.validateClassBelongsToCourse(enrollment.getClassId(), existing.getCourseId());
            // Medium fix: 变更班级时同样需要学员排课时间冲突检测（create 有此校验，update 不能绕过）
            enrollmentService.checkTimeConflict(existing.getStudentId(), enrollment.getClassId());
        }
        // M2 fix: 仅更新 classId，避免 updateById 将 stale 字段写回覆盖并发审核操作
        if (enrollment.getClassId() != null) {
            enrollmentService.updateClassId(id, enrollment.getClassId());
        }
        // L1 fix: 防止并发删除后 getById 返回 null
        Enrollment updated = enrollmentService.getById(id);
        if (updated == null) {
            throw new BusinessException(404, "报名记录不存在");
        }
        return Result.success(updated);
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
