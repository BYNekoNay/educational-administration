package com.pzhu.eduadmin.modules.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.mapper.NotificationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
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
    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @Override
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L); // 30分钟超时
        emitters.put(userId, emitter);

        // M15 fix: 条件移除，防止旧 emitter 超时回调误删新 emitter
        emitter.onCompletion(() -> emitters.remove(userId, emitter));
        emitter.onTimeout(() -> emitters.remove(userId, emitter));
        emitter.onError(e -> emitters.remove(userId, emitter));

        // 发送一条连接成功事件（可选）
        try {
            emitter.send(SseEmitter.event().name("connected").data("ok"));
        } catch (IOException e) {
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
        return true;
    }

    private void emit(Long userId, Notification notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(notification));
            } catch (IOException e) {
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
        if (n != null && n.getUserId().equals(userId)) {
            n.setIsRead(1);
            notificationMapper.updateById(n);
        }
    }
}
