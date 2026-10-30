package com.family.storage.notify;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 站内通知仓储。分页列表（可按未读/类型过滤）、未读计数、全部已读，
 * 以及 InAppChannel 幂等去重所需的 existsByUserIdAndReminderKey。
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserId(Long userId, Pageable pageable);

    Page<Notification> findByUserIdAndReadFlagFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);

    List<Notification> findByUserIdAndReadFlagFalse(Long userId);

    long countByUserIdAndReadFlagFalse(Long userId);

    boolean existsByUserIdAndReminderKey(Long userId, String reminderKey);

    /** 分页列表：unread=true 仅未读；type 非空仅该类型；按创建时间倒序。 */
    @Query("""
            select n from Notification n
            where n.userId = :userId
              and (:unread = false or n.readFlag = false)
              and (:type is null or n.type = :type)
            order by n.createdAt desc
            """)
    Page<Notification> search(@Param("userId") Long userId, @Param("unread") boolean unread,
                              @Param("type") NotificationType type, Pageable pageable);
}