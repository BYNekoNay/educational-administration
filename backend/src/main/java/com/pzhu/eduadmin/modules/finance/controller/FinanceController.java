package com.pzhu.eduadmin.modules.finance.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.finance.entity.*;
import com.pzhu.eduadmin.modules.finance.service.FinanceService;
import com.pzhu.eduadmin.modules.salary.entity.SalaryRule;
import com.pzhu.eduadmin.modules.salary.service.SalaryService;
import com.pzhu.eduadmin.modules.statistics.service.StatisticsService;
import com.pzhu.eduadmin.modules.student.entity.ParentStudent;
import com.pzhu.eduadmin.modules.student.mapper.ParentStudentMapper;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.RequireRole;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FinanceService financeService;
    private final ParentStudentMapper parentStudentMapper;
    private final SalaryService salaryService;
    private final StatisticsService statisticsService;

    // ---- 财务端 ----
    @GetMapping("/payments")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<PaymentRecord>> listPayments(PageQuery query) {
        return Result.success(PageResult.of(financeService.pagePaymentRecords((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @PostMapping("/payments")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PaymentRecord> createPayment(@Valid @RequestBody PaymentRecord record) {
        record.setOperatorId(CurrentUserHolder.get().getUserId());
        record.setOperatorRole("FINANCE");
        if (record.getPayTime() == null) {
            record.setPayTime(LocalDateTime.now());
        }
        return Result.success(financeService.createPayment(record));
    }

    @GetMapping("/refunds")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<RefundRecord>> listRefunds(PageQuery query) {
        return Result.success(PageResult.of(financeService.pageRefundRecords((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @PostMapping("/refunds")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<RefundRecord> createRefund(@Valid @RequestBody RefundRecord record) {
        // M10 fix: 清除客户端不应设置的审核字段
        record.setId(null);
        record.setAuditorId(null);
        record.setApplicantId(CurrentUserHolder.get().getUserId());
        record.setApplicantRole("FINANCE");
        record.setStatus(1);
        return Result.success(financeService.createRefund(record));
    }

    @PutMapping("/refunds/{id}/audit")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<RefundRecord> auditRefund(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Object statusObj = body.get("status");
        if (statusObj == null) throw new BusinessException(400, "审核状态不能为空");
        Integer status;
        try {
            status = statusObj instanceof Integer ? (Integer) statusObj : Integer.parseInt(statusObj.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "审核状态格式不正确");
        }
        if (status != 2 && status != 3) {
            throw new BusinessException(400, "审核状态仅支持 2-通过 或 3-驳回");
        }
        BigDecimal refundAmount;
        try {
            Object ra = body.get("refundAmount");
            refundAmount = ra != null ? new BigDecimal(ra.toString()) : BigDecimal.ZERO;
        } catch (NumberFormatException e) {
            throw new BusinessException(400, "退费金额格式不正确");
        }
        // L1: 审核通过时退费金额必须大于0，防止零元退费与"未提供"不可区分
        if (status == 2 && refundAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "审核通过时退费金额必须大于0");
        }
        return Result.success(financeService.auditRefund(id, status,
                CurrentUserHolder.get().getUserId(), refundAmount));
    }

    @GetMapping("/lesson-accounts")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<LessonAccount>> listLessonAccounts(PageQuery query) {
        return Result.success(PageResult.of(financeService.pageLessonAccounts((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    @GetMapping("/lesson-accounts/{id}")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<LessonAccount> getLessonAccount(@PathVariable Long id) {
        return Result.success(financeService.getLessonAccountById(id));
    }

    @GetMapping("/lesson-flows")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<LessonFlow>> listLessonFlows(PageQuery query) {
        return Result.success(PageResult.of(financeService.pageLessonFlows((int) query.getPageNum(), (int) query.getPageSize(),
                query.getSortField(), query.getSortOrder())));
    }

    // ---- 续费登记（C6） ----

    @PostMapping("/renewals")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PaymentRecord> createRenewal(@Valid @RequestBody PaymentRecord record) {
        record.setOperatorId(CurrentUserHolder.get().getUserId());
        record.setOperatorRole("FINANCE");
        if (record.getPayTime() == null) {
            record.setPayTime(LocalDateTime.now());
        }
        return Result.success(financeService.createPayment(record));
    }

    // ---- 薪资规则（M1） ----

    @GetMapping("/salary-rules")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<PageResult<SalaryRule>> listSalaryRules(PageQuery query) {
        return Result.success(PageResult.of(salaryService.pageSalaryRules((int) query.getPageNum(), (int) query.getPageSize(),
                query.getKeyword(), query.getSortField(), query.getSortOrder())));
    }

    @PostMapping("/salary-rules")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    public Result<SalaryRule> createSalaryRule(@Valid @RequestBody SalaryRule rule) {
        return Result.success(salaryService.createSalaryRule(rule));
    }

    // ---- 营收统计（M3） ----

    @GetMapping("/statistics/revenue")
    @RequireRole({"SUPER_ADMIN", "FINANCE"})
    @SuppressWarnings("unchecked")
    public Result<Map<String, Object>> revenueStats() {
        Map<String, Object> dashboard = statisticsService.getDashboard();
        Map<String, Object> charts = (Map<String, Object>) dashboard.get("charts");
        if (charts == null) return Result.fail(500, "统计数据获取失败");
        return Result.success(Map.of("revenueTrend", charts.get("revenueTrend")));
    }

    // ---- 家长端课时查询（共享 finance 路径） ----

    @GetMapping("/parent/students/{studentId}/lesson-account")
    @RequireRole("PARENT")
    public Result<List<LessonAccount>> childAccountsByStudentId(@PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(financeService.getByStudentId(studentId));
    }

    @GetMapping("/parent/students/{studentId}/lesson-flows")
    @RequireRole("PARENT")
    public Result<List<LessonFlow>> childFlowsByStudentId(@PathVariable Long studentId) {
        Long parentUserId = CurrentUserHolder.get().getUserId();
        checkParentBinding(parentUserId, studentId);
        return Result.success(financeService.getFlowsByStudentId(studentId, 20));
    }

    /**
     * 校验家长是否与学员存在绑定关系（行级数据隔离）
     */
    private void checkParentBinding(Long parentUserId, Long studentId) {
        Long count = parentStudentMapper.selectCount(
                new LambdaQueryWrapper<ParentStudent>()
                        .eq(ParentStudent::getParentUserId, parentUserId)
                        .eq(ParentStudent::getStudentId, studentId));
        if (count == 0) {
            throw new BusinessException(403, "无权访问该学员数据");
        }
    }
}
