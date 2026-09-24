package com.family.storage.stats;

import com.family.storage.category.CategoryRepository;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemStatus;
import com.family.storage.location.LocationRepository;
import com.family.storage.settings.SettingsService;
import com.family.storage.shopping.ShoppingItemRepository;
import com.family.storage.shopping.ShoppingStatus;
import com.family.storage.stats.dto.ItemCountByDimension;
import com.family.storage.stats.dto.SummaryResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 统计业务（spec §5.8）：
 * - summary()：只统计 ACTIVE 物品的总数/总量/总值；expiringSoon 用
 *   expiry.remind.days（默认 7）阈值窗口内 ACTIVE 物品数；pendingShopping 为
 *   PENDING 购物清单项数。
 * - byCategory()/byLocation()：按维度（分类/位置）聚合 ACTIVE 物品的物品数/
 *   总量/总值，维度名经 Category/LocationRepository 的 ID-&gt;name 映射补齐；
 *   该维度下无物品的维度不返回。
 * 便捷 3 参构造器仅供单元测试（维度名仓库为 null 时维度名返回空串）。
 */
@Service
public class StatisticsService {

    private final ItemRepository itemRepository;
    private final ShoppingItemRepository shoppingItemRepository;
    private final SettingsService settingsService;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    @Autowired
    public StatisticsService(ItemRepository itemRepository, ShoppingItemRepository shoppingItemRepository,
                             SettingsService settingsService, CategoryRepository categoryRepository,
                             LocationRepository locationRepository) {
        this.itemRepository = itemRepository;
        this.shoppingItemRepository = shoppingItemRepository;
        this.settingsService = settingsService;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
    }

    /** 便捷构造器（仅供单元测试）：维度名仓库置空。 */
    public StatisticsService(ItemRepository itemRepository, ShoppingItemRepository shoppingItemRepository,
                             SettingsService settingsService) {
        this(itemRepository, shoppingItemRepository, settingsService, null, null);
    }

    public SummaryResponse summary() {
        ItemStatus active = ItemStatus.ACTIVE;
        long totalItems = itemRepository.countByStatus(active);
        long totalQuantity = itemRepository.sumQuantityByStatus(active);
        BigDecimal totalValue = itemRepository.sumValueByStatus(active);
        int days = settingsService.getInt(SettingsService.EXPIRY_REMIND_DAYS,
                SettingsService.DEFAULT_EXPIRY_REMIND_DAYS);
        LocalDate today = LocalDate.now();
        long expiringSoon = itemRepository.countByStatusAndExpiryDateBetween(active, today, today.plusDays(days));
        long pendingShopping = shoppingItemRepository.countByStatus(ShoppingStatus.PENDING);
        return new SummaryResponse(totalItems, totalQuantity, totalValue, expiringSoon, pendingShopping);
    }

    public List<ItemCountByDimension> byCategory() {
        Map<Long, String> names = categoryRepository == null ? Map.of()
                : categoryRepository.findAll().stream()
                        .collect(Collectors.toMap(c -> c.getId(), c -> c.getName()));
        return itemRepository.aggregateByCategory(ItemStatus.ACTIVE).stream()
                .map(r -> toDimension(r, names))
                .toList();
    }

    public List<ItemCountByDimension> byLocation() {
        Map<Long, String> names = locationRepository == null ? Map.of()
                : locationRepository.findAll().stream()
                        .collect(Collectors.toMap(l -> l.getId(), l -> l.getName()));
        return itemRepository.aggregateByLocation(ItemStatus.ACTIVE).stream()
                .map(r -> toDimension(r, names))
                .toList();
    }

    private ItemCountByDimension toDimension(ItemRepository.ItemDimensionAggregate r, Map<Long, String> names) {
        return new ItemCountByDimension(r.getDimensionId(),
                names.getOrDefault(r.getDimensionId(), ""),
                r.getItemCount(), r.getTotalQuantity(), r.getTotalValue());
    }
}