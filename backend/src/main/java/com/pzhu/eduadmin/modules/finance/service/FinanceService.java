package com.pzhu.eduadmin.modules.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.finance.entity.*;

import java.math.BigDecimal;
import java.util.List;

public interface FinanceService {

    // 收费
    Page<PaymentRecord> pagePaymentRecords(int pageNum, int pageSize, String sortField, String sortOrder);

    PaymentRecord createPayment(PaymentRecord record);

    // 退费
    Page<RefundRecord> pageRefundRecords(int pageNum, int pageSize, String sortField, String sortOrder);

    /** 根据学员ID列表查询缴费记录（家长端） */
    List<PaymentRecord> getPaymentsByStudentIds(List<Long> studentIds);

    RefundRecord createRefund(RefundRecord record);

    RefundRecord auditRefund(Long id, Integer status, Long auditorId, BigDecimal refundAmount);

    // 课时账户
    Page<LessonAccount> pageLessonAccounts(int pageNum, int pageSize, String sortField, String sortOrder);

    LessonAccount getLessonAccountById(Long id);

    List<LessonAccount> getByStudentId(Long studentId);

    // 课时流水
    Page<LessonFlow> pageLessonFlows(int pageNum, int pageSize, String sortField, String sortOrder);

    List<LessonFlow> getFlowsByStudentId(Long studentId, int limit);
}
