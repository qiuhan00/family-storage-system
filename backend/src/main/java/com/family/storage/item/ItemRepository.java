package com.family.storage.item;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 物品仓储。Task 3 仅暴露分类/位置删除所需的引用计数；
 * 完整查询方法随 Task 4 物品业务补充。
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
    long countByCategoryId(Long categoryId);

    long countByLocationId(Long locationId);
}