package com.family.storage.notify;

import com.family.storage.common.ApiException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 站内通知业务（spec §5.7）：分页列表（默认按创建时间倒序、可按 unread 过滤）、
 * 未读数、单条已读与全部已读。所有查询/修改均限定在当前登录用户本人；
 * 他人通知视为不存在返回 404 NOTIFICATION_NOT_FOUND。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public Page<Notification> list(Long userId, Boolean unread, NotificationType type, Pageable pageable) {
        Pageable sorted = pageable.getSort().isSorted() ? pageable
                : PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"));
        return notificationRepository.search(userId, Boolean.TRUE.equals(unread), type, sorted);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByUserIdAndReadFlagFalse(userId);
    }

    public Notification read(Long id, Long userId) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> notFound());
        if (!n.getUserId().equals(userId)) {
            throw notFound();
        }
        n.setReadFlag(true);
        notificationRepository.save(n);
        return n;
    }

    public void readAll(Long userId) {
        List<Notification> unread = notificationRepository.findByUserIdAndReadFlagFalse(userId);
        for (Notification n : unread) {
            n.setReadFlag(true);
        }
        notificationRepository.saveAll(unread);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "通知不存在");
    }
}