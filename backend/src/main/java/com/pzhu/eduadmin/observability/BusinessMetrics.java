package com.pzhu.eduadmin.observability;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.pzhu.eduadmin.modules.finance.entity.RefundRecord;
import com.pzhu.eduadmin.modules.finance.mapper.RefundRecordMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BusinessMetrics {

    private final MeterRegistry registry;
    private final RefundRecordMapper refundRecordMapper;

    public BusinessMetrics(MeterRegistry registry, RefundRecordMapper refundRecordMapper) {
        this.registry = registry;
        this.refundRecordMapper = refundRecordMapper;
        registerFailureSeries("eduadmin.login.failures", "invalid", "locked");
        registerFailureSeries("eduadmin.enrollment.failures", "invalid", "conflict", "server");
        registerFailureSeries("eduadmin.payment.failures", "invalid", "conflict", "server");
        registerFailureSeries("eduadmin.lesson_account.cas_failures", "conflict");
        registerFailureSeries("eduadmin.schedule.conflicts", "conflict");
        registerFailureSeries("eduadmin.notification.failures",
                "connect", "emit", "timeout", "connection_error", "dispatch", "unknown");
        Gauge.builder("eduadmin.refunds.pending", this, BusinessMetrics::pendingRefundCount)
                .description("Number of active refund requests waiting for review")
                .register(registry);
    }

    public void recordBusinessFailure(String requestPath, int code, String message) {
        String outcome = outcome(code);
        if (requestPath == null) {
            return;
        }
        if (requestPath.equals("/api/auth/login")) {
            increment("eduadmin.login.failures", code == 429 ? "locked" : "invalid");
        }
        if (requestPath.startsWith("/api/parent/enrollments")
                || requestPath.startsWith("/api/edu/enrollments")) {
            increment("eduadmin.enrollment.failures", outcome);
        }
        if (requestPath.startsWith("/api/finance/payments")
                || requestPath.startsWith("/api/finance/renewals")) {
            increment("eduadmin.payment.failures", outcome);
        }
        if (code == 409 && (requestPath.contains("schedule") || requestPath.contains("room-bookings"))) {
            increment("eduadmin.schedule.conflicts", "conflict");
        }
        if (code == 409 && message != null && message.contains("课时账户") && message.contains("冲突")) {
            increment("eduadmin.lesson_account.cas_failures", "conflict");
        }
    }

    public void recordNotificationConnectionFailure(String stage) {
        String normalizedStage = switch (stage) {
            case "connect", "emit", "timeout", "connection_error", "dispatch" -> stage;
            default -> "unknown";
        };
        increment("eduadmin.notification.failures", normalizedStage);
    }

    private void increment(String name, String reason) {
        registry.counter(name, "reason", reason).increment();
    }

    private void registerFailureSeries(String name, String... reasons) {
        for (String reason : reasons) {
            registry.counter(name, "reason", reason);
        }
    }

    private String outcome(int code) {
        if (code == 409) {
            return "conflict";
        }
        if (code >= 500) {
            return "server";
        }
        return "invalid";
    }

    private double pendingRefundCount() {
        try {
            Long count = refundRecordMapper.selectCount(
                    new LambdaQueryWrapper<RefundRecord>()
                            .eq(RefundRecord::getStatus, 1)
                            .eq(RefundRecord::getIsDeleted, 0));
            return count == null ? 0 : count.doubleValue();
        } catch (RuntimeException error) {
            log.warn("Unable to collect pending refund gauge: {}", error.getClass().getSimpleName());
            return Double.NaN;
        }
    }
}
