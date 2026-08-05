package com.pzhu.eduadmin.observability;

import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessMetricsTest {

    @Test
    void registersCriticalBusinessMetricsBeforeTheFirstFailure() {
        RefundRecordMapper refundMapper = mock(RefundRecordMapper.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        new BusinessMetrics(registry, refundMapper);

        assertThat(registry.find("eduadmin.login.failures").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.enrollment.failures").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.payment.failures").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.lesson_account.cas_failures").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.schedule.conflicts").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.notification.failures").meters()).isNotEmpty();
        assertThat(registry.find("eduadmin.refunds.pending").gauge()).isNotNull();
    }

    @Test
    void recordsBoundedBusinessFailureReasonsAndPendingRefundGauge() {
        RefundRecordMapper refundMapper = mock(RefundRecordMapper.class);
        when(refundMapper.selectCount(any())).thenReturn(3L);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetrics metrics = new BusinessMetrics(registry, refundMapper);

        metrics.recordBusinessFailure("/api/auth/login", 429, "locked");
        metrics.recordBusinessFailure("/api/parent/enrollments", 409, "snapshot changed");
        metrics.recordBusinessFailure("/api/finance/payments", 409, "课时账户更新冲突，请重试");
        metrics.recordBusinessFailure("/api/edu/schedules", 409, "排课冲突");
        metrics.recordNotificationConnectionFailure("unexpected-stage");

        assertThat(registry.get("eduadmin.login.failures").tag("reason", "locked").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.enrollment.failures").tag("reason", "conflict").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.payment.failures").tag("reason", "conflict").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.lesson_account.cas_failures").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.schedule.conflicts").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.notification.failures").tag("reason", "unknown").counter().count())
                .isEqualTo(1);
        assertThat(registry.get("eduadmin.refunds.pending").gauge().value()).isEqualTo(3);
    }
}
