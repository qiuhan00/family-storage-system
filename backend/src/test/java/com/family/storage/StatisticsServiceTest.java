package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.family.storage.category.Category;
import com.family.storage.category.CategoryRepository;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemStatus;
import com.family.storage.location.Location;
import com.family.storage.location.LocationRepository;
import com.family.storage.settings.SettingsService;
import com.family.storage.shopping.ShoppingItemRepository;
import com.family.storage.shopping.ShoppingStatus;
import com.family.storage.stats.StatisticsService;
import com.family.storage.stats.dto.ItemCountByDimension;
import com.family.storage.stats.dto.SummaryResponse;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 统计业务测试（Task 9，spec §5.8）：summary 聚合总量/总值/临期数/待购数；
 * byCategory/byLocation 按维度聚合（仅 ACTIVE）并补齐维度名称。
 * 断言使用 ItemStatus.ACTIVE 枚举字面量（与 Item.status 枚举类型对齐）。
 */
@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock ShoppingItemRepository shoppingItemRepository;
    @Mock SettingsService settingsService;
    @Mock CategoryRepository categoryRepository;
    @Mock LocationRepository locationRepository;

    @Test
    void summaryAggregatesCorrectly() {
        when(settingsService.getInt("expiry.remind.days", 7)).thenReturn(7);
        when(itemRepository.countByStatus(ItemStatus.ACTIVE)).thenReturn(5L);
        when(itemRepository.sumQuantityByStatus(ItemStatus.ACTIVE)).thenReturn(12L);
        when(itemRepository.sumValueByStatus(ItemStatus.ACTIVE)).thenReturn(new BigDecimal("80.50"));
        when(itemRepository.countByStatusAndExpiryDateBetween(eq(ItemStatus.ACTIVE), any(), any())).thenReturn(2L);
        when(shoppingItemRepository.countByStatus(ShoppingStatus.PENDING)).thenReturn(3L);
        var svc = new StatisticsService(itemRepository, shoppingItemRepository, settingsService);
        var s = svc.summary();
        assertEquals(5, s.totalItems());
        assertEquals(12, s.totalQuantity());
        assertEquals(0, new BigDecimal("80.50").compareTo(s.totalValue()));
        assertEquals(2, s.expiringSoon());
        assertEquals(3, s.pendingShopping());
    }

    @Test
    void byCategoryAggregatesWithNames() {
        var row1 = row(1L, 2, 3, "45.00");
        var row2 = row(2L, 1, 2, "10.00");
        when(categoryRepository.findAll()).thenReturn(List.of(category(1L, "食品"), category(2L, "日用品")));
        when(itemRepository.aggregateByCategory(ItemStatus.ACTIVE)).thenReturn(List.of(row1, row2));
        List<ItemCountByDimension> rows = svc().byCategory();
        assertEquals(2, rows.size());
        assertEquals(1L, rows.get(0).dimensionId());
        assertEquals("食品", rows.get(0).dimensionName());
        assertEquals(2, rows.get(0).itemCount());
        assertEquals(3, rows.get(0).totalQuantity());
        assertEquals(0, new BigDecimal("45.00").compareTo(rows.get(0).totalValue()));
        assertEquals(2L, rows.get(1).dimensionId());
        assertEquals("日用品", rows.get(1).dimensionName());
    }

    @Test
    void byLocationAggregatesWithNames() {
        var row20 = row(20L, 3, 6, "120.00");
        when(locationRepository.findAll()).thenReturn(List.of(location(10L, "厨房"), location(20L, "客厅")));
        when(itemRepository.aggregateByLocation(ItemStatus.ACTIVE)).thenReturn(List.of(row20));
        List<ItemCountByDimension> rows = svc().byLocation();
        assertEquals(1, rows.size());
        assertEquals(20L, rows.get(0).dimensionId());
        assertEquals("客厅", rows.get(0).dimensionName());
        assertEquals(3, rows.get(0).itemCount());
        assertEquals(6, rows.get(0).totalQuantity());
        assertEquals(0, new BigDecimal("120.00").compareTo(rows.get(0).totalValue()));
    }

    private StatisticsService svc() {
        return new StatisticsService(itemRepository, shoppingItemRepository, settingsService,
                categoryRepository, locationRepository);
    }

    private ItemRepository.ItemDimensionAggregate row(Long id, long itemCount, long totalQuantity, String totalValue) {
        ItemRepository.ItemDimensionAggregate r = mock(ItemRepository.ItemDimensionAggregate.class);
        when(r.getDimensionId()).thenReturn(id);
        when(r.getItemCount()).thenReturn(itemCount);
        when(r.getTotalQuantity()).thenReturn(totalQuantity);
        when(r.getTotalValue()).thenReturn(new BigDecimal(totalValue));
        return r;
    }

    private Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        return c;
    }

    private Location location(Long id, String name) {
        Location l = new Location();
        l.setId(id);
        l.setName(name);
        return l;
    }
}