package com.pzhu.eduadmin;

import com.pzhu.eduadmin.modules.notification.channel.InAppNotificationChannel;
import com.pzhu.eduadmin.modules.notification.channel.MockSmsNotificationChannel;
import com.pzhu.eduadmin.modules.notification.channel.NotificationChannel;
import com.pzhu.eduadmin.modules.notification.channel.NotificationChannelDispatcher;
import com.pzhu.eduadmin.modules.notification.channel.OutboundEnvelope;
import com.pzhu.eduadmin.observability.BusinessMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 通知外发通道派发器 Mock 单元测试。
 */
@DisplayName("通知外发通道派发器单元测试")
class NotificationChannelDispatcherTest {

    @Test
    @DisplayName("仅向 supports 命中的通道派发")
    void dispatch_onlyMatchingChannelsReceive() {
        NotificationChannel smsChannel = mock(NotificationChannel.class);
        when(smsChannel.supports("SMS")).thenReturn(true);

        NotificationChannel emailChannel = mock(NotificationChannel.class);
        when(emailChannel.supports("SMS")).thenReturn(false);

        NotificationChannelDispatcher dispatcher =
                new NotificationChannelDispatcher(List.of(smsChannel, emailChannel));

        OutboundEnvelope envelope = OutboundEnvelope.builder()
                .userId(100L).type("SCHEDULE_CHANGE").title("t").channelType("SMS").build();

        dispatcher.dispatch(envelope);

        verify(smsChannel).deliver(envelope);
        verify(emailChannel, never()).deliver(any());
    }

    @Test
    @DisplayName("空信封直接忽略，不派发给任何通道")
    void dispatch_nullEnvelopeIgnored() {
        NotificationChannel channel = mock(NotificationChannel.class);
        NotificationChannelDispatcher dispatcher = new NotificationChannelDispatcher(List.of(channel));

        dispatcher.dispatch(null);

        verifyNoInteractions(channel);
    }

    @Test
    @DisplayName("通道 deliver 抛异常被吞掉，不影响其他通道")
    void dispatch_channelExceptionSwallowed() {
        NotificationChannel failing = mock(NotificationChannel.class);
        when(failing.supports("SMS")).thenReturn(true);
        doThrow(new RuntimeException("sms gateway down")).when(failing).deliver(any());

        NotificationChannel healthy = mock(NotificationChannel.class);
        when(healthy.supports("SMS")).thenReturn(true);

        NotificationChannelDispatcher dispatcher =
                new NotificationChannelDispatcher(List.of(failing, healthy));

        OutboundEnvelope envelope = OutboundEnvelope.builder()
                .userId(1L).type("CLASS_REMINDER").channelType("SMS").build();

        assertThatCode(() -> dispatcher.dispatch(envelope)).doesNotThrowAnyException();
        verify(healthy).deliver(envelope);
    }

    @Test
    @DisplayName("InApp 通道仅支持 IN_APP 类型（站内默认通道占位）")
    void inAppChannel_onlySupportsInApp() {
        InAppNotificationChannel channel = new InAppNotificationChannel();
        assertThatCode(() -> {
            org.assertj.core.api.Assertions.assertThat(channel.supports("IN_APP")).isTrue();
            org.assertj.core.api.Assertions.assertThat(channel.supports("SMS")).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("MockSms 通道支持 SMS 且投递记录指标，异常被吞")
    void mockSmsChannel_deliversAndRecords() {
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        MockSmsNotificationChannel sms = new MockSmsNotificationChannel(metrics);

        org.assertj.core.api.Assertions.assertThat(sms.supports("SMS")).isTrue();
        org.assertj.core.api.Assertions.assertThat(sms.supports("IN_APP")).isFalse();

        OutboundEnvelope envelope = OutboundEnvelope.builder()
                .userId(100L).type("RISK_REMINDER").title("续费提醒").content("课时即将到期")
                .dedupeKey("RISK_REMINDER:100:2026-09-07").channelType("SMS").build();

        assertThatCode(() -> sms.deliver(envelope)).doesNotThrowAnyException();
        verify(metrics).recordNotificationExternalDelivered("SMS");
    }

    @Test
    @DisplayName("MockSms 通道指标异常不影响投递（异常吞掉）")
    void mockSmsChannel_metricsFailureSwallowed() {
        BusinessMetrics metrics = mock(BusinessMetrics.class);
        doThrow(new RuntimeException("metrics error"))
                .when(metrics).recordNotificationExternalDelivered("SMS");
        MockSmsNotificationChannel sms = new MockSmsNotificationChannel(metrics);

        OutboundEnvelope envelope = OutboundEnvelope.builder()
                .userId(1L).type("CLASS_REMINDER").channelType("SMS").build();

        assertThatCode(() -> sms.deliver(envelope)).doesNotThrowAnyException();
    }
}
