package com.pzhu.eduadmin.integration;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.course.entity.ClassGroup;
import com.pzhu.eduadmin.modules.course.entity.ClassStudent;
import com.pzhu.eduadmin.modules.course.entity.Course;
import com.pzhu.eduadmin.modules.enrollment.entity.Enrollment;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import com.pzhu.eduadmin.modules.finance.entity.PaymentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 集成测试：报名 → 审核 → 缴费全链路（真实 SQL / 事务 / 约束）。
 * 与 {@code EnrollmentServiceMockTest} 的区别：本测试验证落库结果与数据库约束，而非 mock 交互。
 */
@DisplayName("集成测试：报名缴费链路（真实 DB）")
class EnrollmentPaymentFlowIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("审核通过并缴费后：报名完成、课时入账、写增加流水、建立班级学员关系")
    void enrollmentAuditPayment_createsAccountFlowAndClassStudent() {
        Long studentId = givenStudent("集成学员-缴费");
        Course course = givenCourse("钢琴一对一", 10, "1500.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 10);

        // 报名（待审核）
        Enrollment created = enrollmentService.create(newEnrollment(studentId, course.getId(), classGroup.getId()));
        assertThat(created.getId()).isNotNull();
        assertThat(created.getStatus()).isEqualTo(1);

        // 教务审核通过 → 待缴费，并生成留位截止时间
        Enrollment audited = enrollmentService.audit(created.getId(), 2, AUDITOR_ID, "同意");
        assertThat(audited.getStatus()).isEqualTo(2);
        assertThat(audited.getHoldExpireTime()).isNotNull();
        assertThat(enrollmentMapper.selectById(created.getId()).getStatus()).isEqualTo(2);

        // 财务缴费
        PaymentRecord payment = pay(created.getId(), 10, "1500.00");
        assertThat(payment.getId()).isNotNull();

        // 报名状态推进为已完成
        assertThat(enrollmentMapper.selectById(created.getId()).getStatus()).isEqualTo(3);

        // 课时账户：10 课时入账
        LessonAccount account = accountOf(studentId, course.getId());
        assertThat(account).isNotNull();
        assertThat(account.getTotalLessons()).isEqualByComparingTo("10");
        assertThat(account.getRemainingLessons()).isEqualByComparingTo("10");

        // 课时流水：1 条增加记录，前后余额正确
        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(1);
        LessonFlow flow = flows.get(0);
        assertThat(flow.getSourceType()).isEqualTo(1);
        assertThat(flow.getChangeType()).isEqualTo(1);
        assertThat(flow.getChangeAmount()).isEqualByComparingTo("10");
        assertThat(flow.getBeforeBalance()).isEqualByComparingTo("0");
        assertThat(flow.getAfterBalance()).isEqualByComparingTo("10");

        // 班级学员关系建立且为在班状态
        List<ClassStudent> rows = classStudentsOf(classGroup.getId(), studentId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("同一课程重复报名被拒；被驳回的报名不阻塞重新报名")
    void duplicateActiveEnrollmentRejected_rejectedEnrollmentAllowsReapply() {
        Long studentId = givenStudent("集成学员-重复报名");
        Course course = givenCourse("古筝小组课", 12, "1800.00");
        ClassGroup classGroup = givenClassGroup(course.getId(), 8);

        Enrollment first = enrollmentService.create(newEnrollment(studentId, course.getId(), classGroup.getId()));

        // 存在非终态报名时，重复报名被拒（应用层校验）
        assertThatThrownBy(() -> enrollmentService.create(newEnrollment(studentId, course.getId(), classGroup.getId())))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不可重复报名");

        // 驳回第一条报名（终态 4）后，允许重新报名
        enrollmentService.audit(first.getId(), 4, AUDITOR_ID, "资料不全，请补充");
        Enrollment reapplied = enrollmentService.create(newEnrollment(studentId, course.getId(), classGroup.getId()));
        assertThat(reapplied.getId()).isNotNull();
        assertThat(reapplied.getStatus()).isEqualTo(1);
    }
}