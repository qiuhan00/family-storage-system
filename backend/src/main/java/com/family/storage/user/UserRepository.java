package com.family.storage.user;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储。count() 用于首用户管理员判定；countByEnabledTrue() 供系统状态统计。
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);

    long countByEnabledTrue();
}