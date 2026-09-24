package com.family.storage.shopping;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 购物清单仓储：按状态过滤或全量列表（均按创建时间倒序），
 * 以及统计状态计数（spec §5.8 统计 pendingShopping 使用）。
 */
public interface ShoppingItemRepository extends JpaRepository<ShoppingItem, Long> {
    List<ShoppingItem> findAllByOrderByCreatedAtDesc();

    List<ShoppingItem> findByStatusOrderByCreatedAtDesc(ShoppingStatus status);

    long countByStatus(ShoppingStatus status);
}