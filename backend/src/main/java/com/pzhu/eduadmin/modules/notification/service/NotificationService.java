package com.pzhu.eduadmin.modules.notification.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface NotificationService {

    /** 客户端订阅 SSE 长连接 */
    SseEmitter subscribe(Long userId);

    /** 异步发送通知（持久化 + 推送） */
    void send(Long userId, Notification notification);

    /** 原子发送一次，用于定时提醒去重 */
    boolean sendOnce(Long userId, Notification notification, String dedupeKey);

    /** 批量发送 */
    void sendToUsers(List<Long> userIds, Notification notification);

    /** 分页查询 */
    Page<Notification> listByUser(Long userId, int pageNum, int pageSize);

    /** 未读数 */
    long countUnread(Long userId);

    /** 标记已读（只能标记自己的） */
    void markRead(Long id, Long userId);
}
