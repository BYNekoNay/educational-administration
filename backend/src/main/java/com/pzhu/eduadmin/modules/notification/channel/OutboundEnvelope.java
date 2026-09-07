package com.pzhu.eduadmin.modules.notification.channel;

import lombok.Builder;
import lombok.Data;

/**
 * 外发通知信封。
 *
 * <p>站内通知持久化成功后，由 {@link NotificationChannelDispatcher} 将本信封派发给
 * 各已注册的外部通道（如模拟短信）。字段刻意保持最小——本期不落 outbound_message 表，
 * 仅承载演示/日志/指标所需信息。</p>
 */
@Data
@Builder
public class OutboundEnvelope {

    /** 目标接收人 userId（站内通知的接收方） */
    private Long userId;

    /** 手机号（可空：本期由 MockSms 通道模拟，不真实外呼） */
    private String phone;

    /** 通知标题 */
    private String title;

    /** 通知正文 */
    private String content;

    /** 通知业务类型，如 SCHEDULE_CHANGE / RISK_REMINDER / CLASS_REMINDER */
    private String type;

    /** 关联业务 ID */
    private Long relatedId;

    /** 幂等去重键（透传站内通知的 dedupeKey） */
    private String dedupeKey;

    /** 目标通道类型，如 SMS；通道通过 {@link NotificationChannel#supports(String)} 匹配 */
    private String channelType;
}
