package com.pzhu.eduadmin.modules.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notification.channel.NotificationChannelDispatcher;
import com.pzhu.eduadmin.modules.notification.channel.OutboundEnvelope;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.mapper.NotificationMapper;
import com.pzhu.eduadmin.observability.BusinessMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;
    private final BusinessMetrics businessMetrics;
    private final NotificationChannelDispatcher channelDispatcher;
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    /** 心跳周期配置项（毫秒），默认 25s；低于 60s 的 nginx read 超时即可被刷新 */
    public static final String HEARTBEAT_MS_PROPERTY = "${app.notification.sse.heartbeat-ms:25000}";

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分钟超时
        // M4 fix: 替换前先 complete 旧 emitter，避免多标签页/多设备时旧连接被静默孤立
        // （旧连接不关闭也不收到事件，直到 30 分钟超时才释放）
        SseEmitter old = emitters.put(userId, emitter);
        if (old != null) {
            try {
                old.complete();
            } catch (Exception ignored) {
                // 旧连接可能已断开，忽略
            }
        }

        // M15 fix: 条件移除，防止旧 emitter 超时回调误删新 emitter
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> {
            businessMetrics.recordNotificationConnectionFailure("timeout");
            emitters.remove(userId, emitter);
        });
        emitter.onError(e -> {
            businessMetrics.recordNotificationConnectionFailure("connection_error");
            emitters.remove(userId, emitter);
        });

        // 发送一条连接成功事件（可选）
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException | IllegalStateException e) {
            businessMetrics.recordNotificationConnectionFailure("connect");
            emitters.remove(userId, emitter);
        }
        return emitter;
    }

    @Override
    @Async("sseExecutor")
    public void send(Long userId, Notification notification) {
        notification.setUserId(userId);
        notification.setIsRead(0);
        notificationMapper.insert(notification);

        emit(userId, notification);
        dispatchAfterPersist(userId, notification);
    }

    @Override
    public boolean sendOnce(Long userId, Notification notification, String dedupeKey) {
        notification.setUserId(userId);
        notification.setIsRead(0);
        notification.setDedupeKey(dedupeKey);
        try {
            notificationMapper.insert(notification);
        } catch (DuplicateKeyException ignored) {
            return false;
        }
        emit(userId, notification);
        dispatchAfterPersist(userId, notification);
        return true;
    }

    /**
     * 站内通知入库成功后，将信封交给外发通道派发器（模拟短信等）。
     * 派发失败不影响主流程。
     */
    private void dispatchAfterPersist(Long userId, Notification notification) {
        try {
            if (channelDispatcher == null) {
                return;
            }
            channelDispatcher.dispatch(OutboundEnvelope.builder()
                    .userId(userId)
                    .phone(null)
                    .title(notification.getTitle())
                    .content(notification.getContent())
                    .type(notification.getType())
                    .relatedId(notification.getRelatedId())
                    .dedupeKey(notification.getDedupeKey())
                    // 站内入库已由本类完成，此处信封代表"可外发"的触达（本期为 SMS）
                    .channelType("SMS")
                    .build());
        } catch (Exception e) {
            log.warn("通知外发派发失败，已忽略: userId={}", userId, e);
        }
    }

    // ==================== SSE 心跳 ====================

    /**
     * 周期心跳入口。周期性向所有在线连接发送注释行（对浏览器 EventSource 透明），
     * 防止 nginx 反向代理因长连静默超时而断开 SSE。
     */
    @Scheduled(fixedRateString = HEARTBEAT_MS_PROPERTY)
    public void sendHeartbeat() {
        heartbeatAll();
    }

    /**
     * 心跳执行体：遍历连接池逐个发送注释事件；失败连接移除并记指标。
     * 抽出为 public 方法以便纯 Mockito 单测（测试与实现不在同一包）。
     */
    public void heartbeatAll() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Map.Entry<Long, SseEmitter> entry : emitters.entrySet()) {
            heartbeatEmitter(entry.getKey(), entry.getValue());
        }
    }

    /**
     * 向单个连接发送心跳注释事件。
     *
     * @param userId  用户 ID
     * @param emitter 目标 SSE 连接
     */
    public void heartbeatEmitter(Long userId, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().comment("hb"));
        } catch (Exception e) {
            businessMetrics.recordNotificationConnectionFailure("heartbeat");
            emitters.remove(userId, emitter);
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 已完成/断开的连接无需重复 complete
            }
        }
    }

    private void emit(Long userId, Notification notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (IOException | IllegalStateException e) {
                businessMetrics.recordNotificationConnectionFailure("emit");
                emitters.remove(userId, emitter);
                log.debug("SSE send failed for user {}, removed emitter", userId);
            }
        }
    }

    @Override
    @Async("sseExecutor")
    public void sendToUsers(List<Long> userIds, Notification notification) {
        for (Long userId : userIds) {
            try {
                // 为每个用户创建独立的通知记录
                Notification copy = new Notification();
                copy.setUserId(userId);
                copy.setType(notification.getType());
                copy.setTitle(notification.getTitle());
                copy.setContent(notification.getContent());
                copy.setRelatedId(notification.getRelatedId());
                // M16 fix: 为每个用户追加 userId，防止共享 dedupeKey 导致后续用户通知丢失
                String baseKey = notification.getDedupeKey();
                copy.setDedupeKey(baseKey != null ? baseKey + ":" + userId : null);
                send(userId, copy);
            } catch (Exception e) {
                businessMetrics.recordNotificationConnectionFailure("dispatch");
                log.error("通知发送失败, userId={}", userId, e);
            }
        }
    }

    @Override
    public Page<Notification> listByUser(Long userId, int pageNum, int pageSize) {
        return notificationMapper.selectPage(new Page<>(pageNum, pageSize),
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .orderByDesc(Notification::getCreateTime));
    }

    @Override
    public long countUnread(Long userId) {
        return notificationMapper.selectCount(
                new LambdaQueryWrapper<Notification>()
                        .eq(Notification::getUserId, userId)
                        .eq(Notification::getIsRead, 0));
    }

    @Override
    public void markRead(Long id, Long userId) {
        Notification n = notificationMapper.selectById(id);
        // A6#3 fix: userId 来自当前登录用户必非空，反向调用 equals 避免 n.getUserId() 为 null 时 NPE
        if (n != null && userId.equals(n.getUserId())) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }
}
