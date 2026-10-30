package com.family.storage.notify;

import com.family.storage.common.PageResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 站内通知端点（spec §5.7，/api/notifications）。需登录；
 * 所有数据以当前登录用户为边界（他人通知返回 404）。
 */
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @GetMapping
    public PageResponse<Notification> list(@AuthenticationPrincipal Long userId,
                                           @RequestParam(required = false) Boolean unread,
                                           @RequestParam(required = false) NotificationType type,
                                           Pageable pageable) {
        return PageResponse.of(notificationService.list(userId, unread, type, pageable));
    }

    @GetMapping("/unread-count")
    public Map<String, Long> unreadCount(@AuthenticationPrincipal Long userId) {
        return Map.of("count", notificationService.unreadCount(userId));
    }

    @PostMapping("/{id}/read")
    public Notification read(@AuthenticationPrincipal Long userId, @PathVariable Long id) {
        return notificationService.read(id, userId);
    }

    @PostMapping("/read-all")
    public void readAll(@AuthenticationPrincipal Long userId) {
        notificationService.readAll(userId);
    }
}