package com.pzhu.eduadmin.modules.salary.controller;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.salary.entity.SalaryAdjustment;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.entity.TeacherSalary;
import com.pzhu.eduadmin.modules.salary.service.SalaryService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/finance/salaries")
@RequiredArgsConstructor
public class SalaryController {

    private final SalaryService salaryService;

    // ---- 薪资规则 ----
    @GetMapping("/rules")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<SalaryRule>> listRules(PageQuery query) {
        return Result.success(PageResult.of(salaryService.pageSalaryRules((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @PostMapping("/rules")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<SalaryRule> createRule(@Valid @RequestBody SalaryRule rule) {
        return Result.success(salaryService.createSalaryRule(rule));
    }

    @PutMapping("/rules/{id}")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<SalaryRule> updateRule(@PathVariable Long id, @RequestBody SalaryRule rule) {
        rule.setId(id);
        return Result.success(salaryService.updateSalaryRule(rule));
    }

    @DeleteMapping("/rules/{id}")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<Void> deleteRule(@PathVariable Long id) {
        salaryService.deleteSalaryRule(id);
        return Result.success();
    }

    // ---- 薪资列表 ----
    @GetMapping
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<TeacherSalary>> listSalaries(PageQuery query) {
        return Result.success(PageResult.of(salaryService.pageTeacherSalaries((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @PostMapping({"/calculate", ""})
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> calculate(@RequestBody Map<String, Object> body) {
        Object teacherIdObj = body.get("teacherId");
        if (teacherIdObj == null) throw new BusinessException(400, "teacherId不能为空");
        Long teacherId = Long.valueOf(teacherIdObj.toString());

        String salaryMonth = (String) body.get("salaryMonth");
        if (salaryMonth == null || salaryMonth.isBlank()) throw new BusinessException(400, "salaryMonth不能为空");

        BigDecimal bonusAmount = BigDecimal.ZERO;
        if (body.get("bonusAmount") != null) {
            try {
                bonusAmount = new BigDecimal(body.get("bonusAmount").toString());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "bonusAmount格式不正确");
            }
        }
        return Result.success(salaryService.calculateSalary(salaryMonth, teacherId, bonusAmount));
    }

    @PutMapping("/{id}/confirm")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> confirm(@PathVariable Long id) {
        return Result.success(salaryService.confirmSalary(id));
    }

    @PutMapping("/{id}/void")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> voidSalary(@PathVariable Long id) {
        return Result.success(salaryService.voidSalary(id));
    }

    // ---- 薪资调整 ----
    @GetMapping("/adjustments")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<SalaryAdjustment>> listAdjustments(PageQuery query) {
        return Result.success(PageResult.of(salaryService.pageSalaryAdjustments((int) query.getPageNum(), (int) query.getPageSize())));
    }

    @PostMapping("/adjustments")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<SalaryAdjustment> createAdjustment(@RequestBody Map<String, Object> body) {
        Object salaryIdObj = body.get("salaryId");
        if (salaryIdObj == null) throw new BusinessException(400, "salaryId不能为空");
        Long salaryId = Long.valueOf(salaryIdObj.toString());

        Object adjustAmountObj = body.get("adjustAmount");
        if (adjustAmountObj == null) throw new BusinessException(400, "adjustAmount不能为空");
        BigDecimal adjustAmount;
        try {
            adjustAmount = new BigDecimal(adjustAmountObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "adjustAmount格式不正确");
        }

        String reason = (String) body.get("reason");
        Long operatorId = CurrentUserHolder.get().getUserId();
        return Result.success(salaryService.createAdjustment(salaryId, adjustAmount, reason, operatorId));
    }
}
