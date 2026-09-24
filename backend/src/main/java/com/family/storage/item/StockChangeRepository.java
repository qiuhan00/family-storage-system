package com.family.storage.item;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 出入库记录仓储（spec §5.4 历史查询与删除级联）。
 * findByItemIdOrderByCreatedAtDesc 供变更历史按时间倒序；
 * deleteByItemId 供删除物品时级联清除审计记录（由 ItemService.delete 在同一事务内调用）。
 */
public interface StockChangeRepository extends JpaRepository<StockChange, Long> {
    List<StockChange> findByItemIdOrderByCreatedAtDesc(Long itemId);

    void deleteByItemId(Long itemId);
}