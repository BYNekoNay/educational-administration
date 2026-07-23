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
        // Mass assignment protection: strip server-controlled fields
        rule.setId(null);
        rule.setCreateTime(null);
        rule.setUpdateTime(null);
        return Result.success(salaryService.createSalaryRule(rule));
    }

    @PutMapping("/rules/{id}")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<SalaryRule> updateRule(@PathVariable Long id, @RequestBody SalaryRule rule) {
        rule.setId(id);
        // Mass assignment protection: 剥离服务端控制的审计字段（与 createRule 一致）
        rule.setCreateTime(null);
        rule.setUpdateTime(null);
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
    public Result<PageResult<TeacherSalary>> listSalaries(PageQuery query,
                                                           @RequestParam(required = false) Integer status,
                                                           @RequestParam(required = false) String month) {
        return Result.success(PageResult.of(salaryService.pageTeacherSalaries((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), status, month, query.getSortField(), query.getSortOrder())));
    }

    @PostMapping({"/calculate", ""})
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> calculate(@RequestBody Map<String, Object> body) {
        Object teacherIdObj = body.get("teacherId");
        if (teacherIdObj == null) throw new BusinessException(400, "teacherId不能为空");
        Long teacherId;
        try {
            teacherId = Long.valueOf(teacherIdObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "teacherId格式不正确");
        }

        // M12 fix: 安全类型转换，防止客户端传入数字类型导致 ClassCastException
        Object salaryMonthObj = body.get("salaryMonth");
        String salaryMonth = salaryMonthObj != null ? String.valueOf(salaryMonthObj) : null;
        if (salaryMonth == null || salaryMonth.isBlank()) throw new BusinessException(400, "salaryMonth不能为空");

        BigDecimal bonusAmount = BigDecimal.ZERO;
        if (body.get("bonusAmount") != null) {
            try {
                bonusAmount = new BigDecimal(body.get("bonusAmount").toString());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "bonusAmount格式不正确");
            }
            // L fix: 奖金不能为负，负向调整应走 createAdjustment 通道
            if (bonusAmount.signum() < 0) {
                throw new BusinessException(400, "bonusAmount不能为负数");
            }
        }
        return Result.success(salaryService.calculateSalary(salaryMonth, teacherId, bonusAmount));
    }

    /**
     * 一键结算指定月份的全部教师薪资
     */
    @PostMapping("/calculate-batch")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<Map<String, Object>> calculateBatch(@RequestBody Map<String, Object> body) {
        Object salaryMonthObj = body.get("salaryMonth");
        String salaryMonth = salaryMonthObj != null ? String.valueOf(salaryMonthObj) : null;
        if (salaryMonth == null || salaryMonth.isBlank()) throw new BusinessException(400, "salaryMonth不能为空");

        BigDecimal bonusAmount = BigDecimal.ZERO;
        if (body.get("bonusAmount") != null) {
            try {
                bonusAmount = new BigDecimal(body.get("bonusAmount").toString());
            } catch (NumberFormatException e) {
                throw new BusinessException(400, "bonusAmount格式不正确");
            }
            if (bonusAmount.signum() < 0) {
                throw new BusinessException(400, "bonusAmount不能为负数");
            }
        }
        return Result.success(salaryService.calculateBatchSalary(salaryMonth, bonusAmount));
    }

    @PutMapping("/{id}/confirm")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> confirm(@PathVariable Long id) {
        return Result.success(salaryService.confirmSalary(id));
    }

    @PutMapping("/{id}/pay")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<TeacherSalary> pay(@PathVariable Long id) {
        return Result.success(salaryService.paySalary(id));
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
        Long salaryId;
        try {
            salaryId = Long.valueOf(salaryIdObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "salaryId格式不正确");
        }

        Object adjustAmountObj = body.get("adjustAmount");
        if (adjustAmountObj == null) throw new BusinessException(400, "adjustAmount不能为空");
        BigDecimal adjustAmount;
        try {
            adjustAmount = new BigDecimal(adjustAmountObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "adjustAmount格式不正确");
        }

        // M11 fix: 校验 reason 非空（DB 列为 NOT NULL），并安全转换类型
        Object reasonObj = body.get("reason");
        String reason = reasonObj != null ? String.valueOf(reasonObj) : null;
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(400, "调整原因不能为空");
        }
        Long operatorId = CurrentUserHolder.get().getUserId();
        return Result.success(salaryService.createAdjustment(salaryId, adjustAmount, reason, operatorId));
    }
}
