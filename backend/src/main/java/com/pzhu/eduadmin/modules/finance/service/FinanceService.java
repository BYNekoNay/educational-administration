package com.pzhu.eduadmin.modules.finance.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.finance.dto.LessonAccountAdjustRequest;
import com.pzhu.eduadmin.modules.finance.entity.*;

import java.math.BigDecimal;
import java.util.List;

public interface FinanceService {

    // 收费
    Page<PaymentRecord> pagePaymentRecords(int pageNum, int pageSize, String sortField, String sortOrder, String keyword);

    PaymentRecord createPayment(PaymentRecord record);

    // 退费
    Page<RefundRecord> pageRefundRecords(int pageNum, int pageSize, String sortField, String sortOrder, String keyword);

    /** 根据学员ID列表查询缴费记录（家长端） */
    List<PaymentRecord> getPaymentsByStudentIds(List<Long> studentIds);

    RefundRecord createRefund(RefundRecord record);

    RefundRecord auditRefund(Long id, Integer status, Long auditorId, BigDecimal refundAmount);

    /**
     * 带审核备注的重载（V14 新增）。旧签名保留给既有调用与测试，内部转调本方法。
     */
    RefundRecord auditRefund(Long id, Integer status, Long auditorId, BigDecimal refundAmount, String remark);

    /** 退费状态分布：待审 / 通过 / 驳回 计数 + 通过金额合计 */
    java.util.Map<String, Object> refundStats();

    // 课时账户
    Page<LessonAccount> pageLessonAccounts(int pageNum, int pageSize, String sortField, String sortOrder, String keyword);

    /** 按 学员+课程 定位课时账户（收费页「调整课时」入口用），不存在返回 null */
    LessonAccount getAccountByStudentAndCourse(Long studentId, Long courseId);

    LessonAccount getLessonAccountById(Long id);

    List<LessonAccount> getByStudentId(Long studentId);

    /**
     * 人工调整课时（补课/扣课）。
     *
     * <p>区别于考勤扣减：扣减超过剩余课时时直接拒绝（不做截断到 0），
     * 避免把输入错误静默变成真实账目变化。</p>
     *
     * @param accountId lesson_account 主键
     * @param request   方向、数量、原因、幂等键
     * @return 新写入的课时流水
     */
    LessonFlow adjustLessons(Long accountId, LessonAccountAdjustRequest request);

    // 课时流水
    Page<LessonFlow> pageLessonFlows(int pageNum, int pageSize, String sortField, String sortOrder, String keyword);

    List<LessonFlow> getFlowsByStudentId(Long studentId, int limit);
}
