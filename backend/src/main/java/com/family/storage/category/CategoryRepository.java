package com.family.storage.category;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 分类仓储。列表按 sortOrder 升序返回（spec §5.3 列表顺序）。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAsc();
}