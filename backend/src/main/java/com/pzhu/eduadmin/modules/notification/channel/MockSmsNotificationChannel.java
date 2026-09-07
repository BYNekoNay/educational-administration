package com.pzhu.eduadmin.modules.notification.channel;

import com.pzhu.eduadmin.observability.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 模拟短信通道（默认关闭）。
 *
 * <p>通过配置项 {@code app.notification.mock-sms.enabled=true} 打开后，站内通知
 * 持久化成功会同步"模拟外发"一条短信（仅打印 [MOCK-SMS] 日志 + 记录指标），
 * 不接真实短信网关、不影响主链路。任何投递异常都在本类内吞掉。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification.mock-sms", name = "enabled", havingValue = "true")
public class MockSmsNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_TYPE = "SMS";

    private final BusinessMetrics businessMetrics;

    @Override
    public boolean supports(String channelType) {
        return CHANNEL_TYPE.equals(channelType);
    }

    @Override
    public void deliver(OutboundEnvelope envelope) {
        try {
            String receiver = envelope.getPhone() != null && !envelope.getPhone().isBlank()
                    ? envelope.getPhone()
                    : ("userId=" + envelope.getUserId());
            log.info("[MOCK-SMS] 模拟短信发送: to={}, type={}, title={}, content={}, dedupeKey={}",
                    receiver, envelope.getType(), envelope.getTitle(), envelope.getContent(), envelope.getDedupeKey());
            businessMetrics.recordNotificationExternalDelivered(CHANNEL_TYPE);
        } catch (Exception e) {
            log.warn("[MOCK-SMS] 模拟短信派发失败，已忽略（不影响主流程）: {}", e.getMessage());
        }
    }
}
