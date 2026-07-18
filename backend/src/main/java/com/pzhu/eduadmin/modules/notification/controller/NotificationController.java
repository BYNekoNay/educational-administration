package com.pzhu.eduadmin.modules.notification.controller;

import com.pzhu.eduadmin.common.PageQuery;
import com.pzhu.eduadmin.common.PageResult;
import com.pzhu.eduadmin.common.Result;
import com.pzhu.eduadmin.modules.notification.service.NotificationService;
import com.pzhu.eduadmin.security.CurrentUserHolder;
import com.pzhu.eduadmin.security.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    @GetMapping("/notifications/stream")
    public SseEmitter stream(@RequestParam String token) {
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            throw new RuntimeException("Invalid token");
        }
        Long userId = Long.valueOf(claims.getSubject());
        return notificationService.subscribe(userId);
    }

    @GetMapping("/notifications")
    public Result<PageResult<?>> list(PageQuery query) {
        Long userId = CurrentUserHolder.get().getUserId();
        return Result.success(PageResult.of(notificationService.listByUser(userId,
                (int) query.getPageNum(), (int) query.getPageSize())));
    }

    @GetMapping("/notifications/unread-count")
    public Result<Long> unreadCount() {
        Long userId = CurrentUserHolder.get().getUserId();
        return Result.success(notificationService.countUnread(userId));
    }

    @PutMapping("/notifications/{id}/read")
    public Result<Void> markRead(@PathVariable Long id) {
        Long userId = CurrentUserHolder.get().getUserId();
        notificationService.markRead(id, userId);
        return Result.success();
    }
}
