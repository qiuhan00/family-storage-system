package com.family.storage.category;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 分类仓储。列表按 sortOrder 升序返回（spec §5.3 列表顺序）；
 * findByName 用于重名校验（DB uk_categories_name 约束前先给 400）。
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findAllByOrderBySortOrderAsc();

    Optional<Category> findByName(String name);
}