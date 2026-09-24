package com.family.storage.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。count() 用于首用户管理员判定；countByEnabledTrue() 供系统状态统计；
 * findByEnabledTrue() 供临期提醒调度器获取全部启用成员。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    long countByEnabledTrue();

    long countByEnabledTrueAndRole(Role role);

    boolean existsByIdAndEnabledTrue(Long id);

    List<User> findByEnabledTrue();
}