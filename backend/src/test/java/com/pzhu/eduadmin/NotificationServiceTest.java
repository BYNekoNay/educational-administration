package com.pzhu.eduadmin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.pzhu.eduadmin.modules.notification.entity.Notification;
import com.pzhu.eduadmin.modules.notification.mapper.NotificationMapper;
import com.pzhu.eduadmin.modules.notification.service.NotificationServiceImpl;
import com.pzhu.eduadmin.observability.BusinessMetrics;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock private NotificationMapper notificationMapper;
    @Mock private BusinessMetrics businessMetrics;
    @InjectMocks private NotificationServiceImpl notificationService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        MapperBuilderAssistant asst = new MapperBuilderAssistant(cfg, "");
        TableInfoHelper.initTableInfo(asst, Notification.class);
    }

    @Test
    @DisplayName("subscribe — 应返回 SSE emitter 并注册到连接池")
    void subscribe_returnsEmitter() {
        SseEmitter emitter = notificationService.subscribe(100L);
        assertThat(emitter).isNotNull();
        emitter.complete();
    }

    @Test
    @DisplayName("send — 应持久化通知并推送给在线用户")
    void send_persistsAndEmits() {
        Notification n = new Notification();
        n.setUserId(100L);
        n.setType("CLASS_REMINDER");
        n.setTitle("新课提醒");

        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        notificationService.send(100L, n);

        verify(notificationMapper).insert(any(Notification.class));
    }

    @Test
    @DisplayName("sendOnce — 重复幂等键不重复发送")
    void sendOnce_duplicateKeyReturnsFalse() {
        Notification notification = new Notification();
        notification.setType("CLASS_REMINDER");
        notification.setTitle("上课提醒");
        doThrow(new DuplicateKeyException("duplicate"))
                .when(notificationMapper).insert(any(Notification.class));

        boolean sent = notificationService.sendOnce(100L, notification, "CLASS_REMINDER:100:10");

        assertThat(sent).isFalse();
        assertThat(notification.getDedupeKey()).isEqualTo("CLASS_REMINDER:100:10");
    }

    @Test
    @DisplayName("sendToUsers — 应为每个用户创建独立通知")
    void sendToUsers_batch() {
        Notification n = new Notification();
        n.setType("ANNOUNCEMENT");
        n.setTitle("公告");

        when(notificationMapper.insert(any(Notification.class))).thenReturn(1);
        notificationService.sendToUsers(List.of(1L, 2L, 3L), n);

        verify(notificationMapper, times(3)).insert(any(Notification.class));
    }

    @Test
    @DisplayName("sendToUsers — 单个用户发送失败不影响其余用户")
    void sendToUsers_errorIsolation() {
        Notification n = new Notification();
        n.setType("ANNOUNCEMENT");
        n.setTitle("公告");

        // 第2个用户插入时抛异常，其余正常
        when(notificationMapper.insert(any(Notification.class)))
                .thenReturn(1)
                .thenThrow(new RuntimeException("DB error"))
                .thenReturn(1);

        notificationService.sendToUsers(List.of(1L, 2L, 3L), n);

        // 所有3个用户都应尝试插入（错误被隔离）
        verify(notificationMapper, times(3)).insert(any(Notification.class));
    }

    @Test
    @DisplayName("countUnread — 应返回未读通知数")
    void countUnread_returnsCount() {
        when(notificationMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(5L);
        assertThat(notificationService.countUnread(100L)).isEqualTo(5L);
    }

    @Test
    @DisplayName("markRead — 应更新is_read=1")
    void markRead_updatesStatus() {
        Notification n = new Notification();
        n.setId(1L);
        n.setUserId(100L);
        n.setIsRead(0);
        when(notificationMapper.selectById(1L)).thenReturn(n);
        when(notificationMapper.updateById(any(Notification.class))).thenReturn(1);

        notificationService.markRead(1L, 100L);
        assertThat(n.getIsRead()).isEqualTo(1);
    }

    @Test
    @DisplayName("listByUser — 应返回分页通知列表")
    void listByUser_returnsPage() {
        Page<Notification> mp = new Page<>(1, 10);
        mp.setRecords(List.of(new Notification()));
        when(notificationMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class))).thenReturn(mp);

        Page<Notification> result = notificationService.listByUser(100L, 1, 10);
        assertThat(result.getRecords()).hasSize(1);
    }
}
