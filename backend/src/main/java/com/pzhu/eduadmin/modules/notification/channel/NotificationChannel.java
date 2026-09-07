package com.pzhu.eduadmin.modules.notification.channel;

/**
 * 通知外发通道抽象。
 *
 * <p>站内通知（InApp）始终由 {@code NotificationServiceImpl} 负责入库 + SSE 推送，
 * 属于默认通道。额外通道（如模拟短信）实现本接口并由 Spring 收集进
 * {@link NotificationChannelDispatcher}，用于扩展"站内之外"的触达。</p>
 */
public interface NotificationChannel {

    /**
     * 是否支持派发指定类型的通道。
     *
     * @param channelType 目标通道类型，如 "SMS" / "IN_APP"
     * @return true 表示该通道会处理该类型信封
     */
    boolean supports(String channelType);

    /**
     * 派发信封。实现方必须自行吞掉异常，禁止影响通知主链路。
     *
     * @param envelope 外发信封
     */
    void deliver(OutboundEnvelope envelope);
}
