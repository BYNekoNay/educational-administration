package com.pzhu.eduadmin.modules.notification.channel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通知通道组合派发器。
 *
 * <p>由 Spring 收集容器内所有 {@link NotificationChannel} 实现，遍历匹配
 * {@code envelope.channelType} 的通道并派发。被 {@code NotificationServiceImpl}
 * 在站内通知入库成功后调用；单个通道异常不会向上抛出（逐通道 try-catch），
 * 保证外部触达失败不阻塞通知主链路。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationChannelDispatcher {

    private final List<NotificationChannel> channels;

    /**
     * 向匹配的通道派发信封。
     *
     * @param envelope 外发信封（可空：空则直接忽略）
     */
    public void dispatch(OutboundEnvelope envelope) {
        if (envelope == null) {
            return;
        }
        for (NotificationChannel channel : channels) {
            try {
                if (channel.supports(envelope.getChannelType())) {
                    channel.deliver(envelope);
                }
            } catch (Exception e) {
                // 外部通道异常吞掉，避免影响通知主链路
                log.warn("通知外发通道派发异常，已忽略: channel={}, userId={}",
                        channel.getClass().getSimpleName(), envelope.getUserId(), e);
            }
        }
    }
}
