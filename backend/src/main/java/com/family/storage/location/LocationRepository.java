package com.family.storage.location;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 位置仓储。findAllByOrderBySortOrderAsc 供树组装（同层按 sortOrder 排序）。
 */
public interface LocationRepository extends JpaRepository<Location, Long> {
    List<Location> findAllByOrderBySortOrderAsc();

    boolean existsByParentId(Long parentId);
}