package com.pzhu.eduadmin.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集成测试：退费申请 / 审核 / 课时回退（真实事务与唯一约束）。
 * 重点验证"同一报名仅一条待审核退费"的数据库生成列 + 唯一索引兜底（对应 Flyway V7 口径）。
 */
@DisplayName("集成测试：退费与课时回退（真实 DB）")
class RefundFlowIntegrationTest extends IntegrationTestSupport {

    private RefundRecord newRefund(Long enrollmentId, Long paymentRecordId, Long studentId, String lessonCount) {
        RefundRecord refund = new RefundRecord();
        refund.setEnrollmentId(enrollmentId);
        refund.setPaymentRecordId(paymentRecordId);
        refund.setStudentId(studentId);
        refund.setApplicantId(PARENT_ID);
        refund.setApplicantRole("PARENT");
        refund.setAmount(BigDecimal.ZERO);
        refund.setLessonCount(new BigDecimal(lessonCount));
        return refund;
    }

    @Test
    @DisplayName("全额退费：课时回退至 0、写负向流水、报名与班级关系同步关闭")
    void fullRefund_reversesLessonsAndClosesEnrollment() {
        Long studentId = givenStudent("集成学员-退费");
        Course course = givenCourse("舞蹈启蒙", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        PaymentRecord payment = givenPaidEnrollment(studentId, course, classGroup, 10, "1500.00");
        LessonAccount account = accountOf(studentId, course.getId());
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("10");

        // 家长提交退费申请（待审核）
        RefundRecord created = financeService.createRefund(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "10"));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(1);

        // 财务审核通过，未显式指定金额 → 按 已缴金额×剩余/购买 自动计算
        RefundRecord approved = financeService.auditRefund(created.getId(), 2, AUDITOR_ID, null);
        assertThat(approved.getStatus()).isEqualTo(2);
        assertThat(approved.getAmount()).isEqualByComparingTo("1500");

        // 课时账户回退至 0
        LessonAccount after = accountOf(studentId, course.getId());
        assertThat(after.getRemainingLessons()).isEqualByComparingTo("0");
        assertThat(after.getTotalLessons()).isEqualByComparingTo("0");

        // 流水：缴费 +10 与退费 -10，余额链路正确
        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(2);
        LessonFlow refundFlow = flows.get(1);
        assertThat(refundFlow.getSourceType()).isEqualTo(4);
        assertThat(refundFlow.getChangeAmount()).isEqualByComparingTo("-10");
        assertThat(refundFlow.getBeforeBalance()).isEqualByComparingTo("10");
        assertThat(refundFlow.getAfterBalance()).isEqualByComparingTo("0");

        // 报名置为已退费(6)，班级学员关系关闭(3)
        assertThat(enrollmentMapper.selectById(payment.getEnrollmentId()).getStatus()).isEqualTo(6);
        List<ClassStudent> rows = classStudentsOf(classGroup.getId(), studentId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(3);
    }

    @Test
    @DisplayName("同一报名重复提交待审核退费：服务层拦截 + 数据库唯一约束兜底；审核后约束释放")
    void duplicatePendingRefund_rejectedAtServiceAndByDatabaseConstraint() {
        Long studentId = givenStudent("集成学员-重复退费");
        Course course = givenCourse("小提琴", 10, "2000.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        PaymentRecord payment = givenPaidEnrollment(studentId, course, classGroup, 10, "2000.00");

        RefundRecord first = financeService.createRefund(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "10"));

        // 服务层校验：同一报名已有待审核退费
        assertThatThrownBy(() -> financeService.createRefund(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "5")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已有待审核");

        // 数据库层兜底：绕过服务校验直接插库，仍被生成列唯一索引拒绝（对应 V7 约束）
        assertThatThrownBy(() -> refundRecordMapper.insert(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "5")))
                .isInstanceOf(DuplicateKeyException.class);

        // 原申请审核通过（终态）后，计算列置 NULL，唯一键释放，可再次插入待审核记录
        financeService.auditRefund(first.getId(), 2, AUDITOR_ID, null);
        RefundRecord released = newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "1");
        refundRecordMapper.insert(released);
        assertThat(released.getId()).isNotNull();
        assertThat(refundRecordMapper.selectCount(new LambdaQueryWrapper<RefundRecord>()
                .eq(RefundRecord::getEnrollmentId, payment.getEnrollmentId()))).isEqualTo(2);
    }

    @Test
    @DisplayName("退费金额超过可退上限：审核被拒且无副作用（申请仍待审核、账户不变）")
    void refundAmountOverCap_rejectedWithoutSideEffects() {
        Long studentId = givenStudent("集成学员-超额退费");
        Course course = givenCourse("素描基础", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        PaymentRecord payment = givenPaidEnrollment(studentId, course, classGroup, 10, "1500.00");

        RefundRecord created = financeService.createRefund(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "10"));

        // 申请金额 9999 > 可退上限 1500 → 409
        assertThatThrownBy(() -> financeService.auditRefund(created.getId(), 2, AUDITOR_ID, new BigDecimal("9999")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过可退上限");

        // 无副作用：申请仍为待审核，课时账户与流水未变化
        assertThat(refundRecordMapper.selectById(created.getId()).getStatus()).isEqualTo(1);
        LessonAccount account = accountOf(studentId, course.getId());
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("10");
        assertThat(flowsOf(account.getId())).hasSize(1);
    }

    @Test
    @DisplayName("全额退费后重新报名缴费：班级学员关系恢复在班，不触发唯一键冲突")
    void afterFullRefund_reEnrollmentRestoresClassStudent() {
        Long studentId = givenStudent("集成学员-退费重报");
        Course course = givenCourse("街舞提高班", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);
        PaymentRecord payment = givenPaidEnrollment(studentId, course, classGroup, 10, "1500.00");

        // 全额退费：账户清零、报名退费(6)、班级关系退出(3)
        RefundRecord refund = financeService.createRefund(
                newRefund(payment.getEnrollmentId(), payment.getId(), studentId, "10"));
        financeService.auditRefund(refund.getId(), 2, AUDITOR_ID, null);
        assertThat(enrollmentMapper.selectById(payment.getEnrollmentId()).getStatus()).isEqualTo(6);
        assertThat(classStudentsOf(classGroup.getId(), studentId).get(0).getStatus()).isEqualTo(3);

        // 重新报名 → 审核 → 缴费
        Enrollment reapplied = enrollmentService.create(newEnrollment(studentId, course.getId(), classGroup.getId()));
        assertThat(reapplied.getStatus()).isEqualTo(1);
        enrollmentService.audit(reapplied.getId(), 2, AUDITOR_ID, "重新报名");
        pay(reapplied.getId(), 5, "750.00");

        // 班级学员关系被恢复为在班（唯一键 (class_id, student_id) 未冲突，仍只有一行）
        List<ClassStudent> rows = classStudentsOf(classGroup.getId(), studentId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(1);

        // 课时账户重新入账 5 课时；报名完成；流水累计 3 条（+10、-10、+5）
        LessonAccount account = accountOf(studentId, course.getId());
        assertThat(account.getTotalLessons()).isEqualByComparingTo("5");
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("5");
        assertThat(enrollmentMapper.selectById(reapplied.getId()).getStatus()).isEqualTo(3);
        assertThat(flowsOf(account.getId())).hasSize(3);
    }
}