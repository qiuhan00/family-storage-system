package com.family.storage.notify;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 站内通知仓储。unread 列表/计数、按用户分页（默认倒序见 Service）、
 * 以及 InAppChannel 幂等去重所需的 existsByUserIdAndReminderKey。
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndReadFlagFalse(Long userId);

    long countByUserIdAndReadFlagFalse(Long userId);

    boolean existsByUserIdAndReminderKey(Long userId, String reminderKey);
}