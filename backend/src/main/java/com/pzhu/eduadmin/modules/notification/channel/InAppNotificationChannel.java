package com.pzhu.eduadmin.modules.notification.channel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 站内通知通道占位实现。
 *
 * <p>站内通知的"入库 + SSE 推送"由 {@code NotificationServiceImpl} 在调用通道前
 * 已完成，因此本通道的 deliver 无需重复投递，仅作为"站内即默认通道"的语义占位
 * 并记录一条 trace 日志，方便在调用链上观察派发器遍历结果。</p>
 */
@Slf4j
@Component
public class InAppNotificationChannel implements NotificationChannel {

    public static final String CHANNEL_TYPE = "IN_APP";

    @Override
    public boolean supports(String channelType) {
        return CHANNEL_TYPE.equals(channelType);
    }

    @Override
    public void deliver(OutboundEnvelope envelope) {
        log.trace("[IN-APP] 站内通知已入库并推送: userId={}, type={}", envelope.getUserId(), envelope.getType());
    }
}
