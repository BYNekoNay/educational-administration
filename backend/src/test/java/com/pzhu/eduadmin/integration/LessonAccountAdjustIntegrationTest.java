package com.pzhu.eduadmin.integration;

import com.pzhu.eduadmin.common.BusinessException;
import com.pzhu.eduadmin.modules.finance.dto.LessonAccountAdjustRequest;
import com.pzhu.eduadmin.modules.finance.entity.LessonAccount;
import com.pzhu.eduadmin.modules.finance.entity.LessonFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 课时人工调整的集成测试：验证真实 SQL 下的余额变更、流水落库、
 * 事务回滚与 biz_no 唯一键幂等（这些行为 Mockito 单测无法覆盖）。
 */
@DisplayName("课时人工调整 - 集成测试")
class LessonAccountAdjustIntegrationTest extends IntegrationTestSupport {

    private LessonAccountAdjustRequest request(int direction, String amount, String bizNo) {
        LessonAccountAdjustRequest r = new LessonAccountAdjustRequest();
        r.setDirection(direction);
        r.setAmount(new BigDecimal(amount));
        r.setRemark("集成测试调整");
        r.setBizNo(bizNo);
        return r;
    }

    @Test
    @DisplayName("增加课时 - 剩余与总课时同步增加，流水落库且带 biz_no")
    void shouldPersistIncrease() {
        loginAs("FINANCE");
        Long studentId = givenStudent("集成学员A");
        givenAccount(studentId, 100L, "10.00", 0);
        LessonAccount account = accountOf(studentId, 100L);

        financeService.adjustLessons(account.getId(), request(1, "5.00", "it-biz-001"));

        LessonAccount updated = lessonAccountMapper.selectById(account.getId());
        assertThat(updated.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(updated.getTotalLessons()).isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(updated.getVersion()).isEqualTo(1);

        List<LessonFlow> flows = flowsOf(account.getId());
        assertThat(flows).hasSize(1);
        LessonFlow flow = flows.get(0);
        assertThat(flow.getSourceType()).isEqualTo(5);
        assertThat(flow.getChangeType()).isEqualTo(4);
        assertThat(flow.getBizNo()).isEqualTo("it-biz-001");
        assertThat(flow.getChangeAmount()).isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(flow.getAfterBalance()).isEqualByComparingTo(new BigDecimal("15.00"));
    }

    @Test
    @DisplayName("同一 bizNo 提交两次 - 只生效一次（幂等由唯一键 + 前置查重共同保证）")
    void shouldBeIdempotentByBizNo() {
        loginAs("FINANCE");
        Long studentId = givenStudent("集成学员B");
        givenAccount(studentId, 100L, "10.00", 0);
        LessonAccount account = accountOf(studentId, 100L);

        financeService.adjustLessons(account.getId(), request(1, "5.00", "it-biz-dup"));
        financeService.adjustLessons(account.getId(), request(1, "5.00", "it-biz-dup"));

        assertThat(accountOf(studentId, 100L).getRemainingLessons())
                .isEqualByComparingTo(new BigDecimal("15.00"));
        assertThat(flowsOf(account.getId())).hasSize(1);
    }

    @Test
    @DisplayName("扣减超过剩余课时 - 抛异常且账户与流水均未变化")
    void shouldRollbackOnOverDeduct() {
        loginAs("FINANCE");
        Long studentId = givenStudent("集成学员C");
        givenAccount(studentId, 100L, "3.00", 0);
        LessonAccount account = accountOf(studentId, 100L);

        assertThatThrownBy(() -> financeService.adjustLessons(account.getId(), request(2, "5.00", "it-biz-over")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("超过剩余课时");

        assertThat(accountOf(studentId, 100L).getRemainingLessons())
                .isEqualByComparingTo(new BigDecimal("3.00"));
        assertThat(accountOf(studentId, 100L).getVersion()).isEqualTo(0);
        assertThat(flowsOf(account.getId())).isEmpty();
    }

    @Test
    @DisplayName("连续两次调整 - version 递增两次且都成功（CAS 序列不卡死）")
    void shouldAllowSequentialAdjustments() {
        loginAs("FINANCE");
        Long studentId = givenStudent("集成学员D");
        givenAccount(studentId, 100L, "10.00", 0);
        LessonAccount account = accountOf(studentId, 100L);

        financeService.adjustLessons(account.getId(), request(1, "2.00", "it-biz-s1"));
        financeService.adjustLessons(account.getId(), request(2, "1.00", "it-biz-s2"));

        LessonAccount updated = accountOf(studentId, 100L);
        assertThat(updated.getRemainingLessons()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(updated.getTotalLessons()).isEqualByComparingTo(new BigDecimal("11.00"));
        assertThat(updated.getVersion()).isEqualTo(2);
        assertThat(flowsOf(account.getId())).hasSize(2);
    }
}
