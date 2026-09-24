package com.family.storage.item;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 物品仓储：分页检索、临期查询，以及分类/位置删除所需的引用计数。
 * search 的 expiring 过滤以 from/to 两个日期参数表达（等价于
 * {@code (:exp=false or expiry_date between :today and :limit)} 语义）：
 * 未启用时传 null，启用时传 [today, today+days]（between 含端点）。
 */
public interface ItemRepository extends JpaRepository<Item, Long> {
    long countByCategoryId(Long categoryId);

    long countByLocationId(Long locationId);

    @Query("""
            select i from Item i
            where (:kw is null or i.name like concat('%', :kw, '%'))
              and (:cat is null or i.categoryId = :cat)
              and (:loc is null or i.locationId = :loc)
              and (:st is null or i.status = :st)
              and (:from is null or (i.expiryDate is not null and i.expiryDate between :from and :to))
            """)
    Page<Item> search(@Param("kw") String keyword, @Param("cat") Long categoryId,
                      @Param("loc") Long locationId, @Param("st") ItemStatus status,
                      @Param("from") LocalDate from, @Param("to") LocalDate to,
                      Pageable pageable);

    List<Item> findByStatusAndExpiryDateBetweenOrderByExpiryDateAsc(ItemStatus status,
                                                                    LocalDate from, LocalDate to);

    // ---- Task 9 统计聚合（spec §5.8）：全部仅统计 ACTIVE 物品 ----

    /** ACTIVE 物品总数。 */
    @Query("select count(i) from Item i where i.status = :status")
    long countByStatus(@Param("status") ItemStatus status);

    /** ACTIVE 物品数量总和。 */
    @Query("select coalesce(sum(i.quantity), 0) from Item i where i.status = :status")
    long sumQuantityByStatus(@Param("status") ItemStatus status);

    /** ACTIVE 物品价值总和（purchasePrice * quantity）。 */
    @Query("select coalesce(sum(i.purchasePrice * i.quantity), 0) from Item i where i.status = :status")
    BigDecimal sumValueByStatus(@Param("status") ItemStatus status);

    /** 指定状态且到期日落在 [from, to] 区间的物品数（临期提醒窗口计数）。 */
    long countByStatusAndExpiryDateBetween(ItemStatus status, LocalDate from, LocalDate to);

    /** 按分类聚合（dimensionId = categoryId）。 */
    @Query("""
            select i.categoryId as dimensionId, count(i) as itemCount,
                   coalesce(sum(i.quantity), 0) as totalQuantity,
                   coalesce(sum(i.purchasePrice * i.quantity), 0) as totalValue
            from Item i
            where i.status = :status
            group by i.categoryId
            """)
    List<ItemDimensionAggregate> aggregateByCategory(@Param("status") ItemStatus status);

    /** 按位置聚合（dimensionId = locationId）。 */
    @Query("""
            select i.locationId as dimensionId, count(i) as itemCount,
                   coalesce(sum(i.quantity), 0) as totalQuantity,
                   coalesce(sum(i.purchasePrice * i.quantity), 0) as totalValue
            from Item i
            where i.status = :status
            group by i.locationId
            """)
    List<ItemDimensionAggregate> aggregateByLocation(@Param("status") ItemStatus status);

    /** 统计维度聚合行投影：dimensionId 为 categoryId / locationId。 */
    interface ItemDimensionAggregate {
        Long getDimensionId();

        long getItemCount();

        long getTotalQuantity();

        BigDecimal getTotalValue();
    }
}