package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.family.storage.category.CategoryRepository;
import com.family.storage.common.ApiException;
import com.family.storage.shopping.ShoppingItem;
import com.family.storage.shopping.ShoppingItemRepository;
import com.family.storage.shopping.ShoppingItemService;
import com.family.storage.shopping.ShoppingStatus;
import com.family.storage.shopping.dto.ShoppingItemResponse;
import com.family.storage.shopping.dto.ShoppingRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 购物清单业务测试（Task 6，spec §5.5）：
 * 数量必须为正、分类必须存在、状态流转（BOUGHT/CANCELLED 写完成时间、
 * 回到 PENDING 清空），以及不存在 id 统一 404 SHOPPING_ITEM_NOT_FOUND。
 */
@ExtendWith(MockitoExtension.class)
class ShoppingServiceTest {
    @Mock ShoppingItemRepository repo;
    @Mock CategoryRepository categoryRepository;

    private ShoppingItemService svc() {
        return new ShoppingItemService(repo, categoryRepository);
    }

    private ShoppingRequest req(int quantity) {
        return new ShoppingRequest("盐", quantity, "袋", null, null);
    }

    private ShoppingItem item(Long id, ShoppingStatus status) {
        ShoppingItem i = new ShoppingItem();
        i.setId(id);
        i.setName("盐");
        i.setQuantity(2);
        i.setUnit("袋");
        i.setStatus(status);
        i.setCreatedBy(7L);
        i.setCreatedAt(LocalDateTime.of(2026, 9, 24, 10, 0));
        return i;
    }

    @Test
    void createRequiresQuantityPositive() {
        var svc = new ShoppingItemService(repo, null);
        assertThrows(ApiException.class, () -> svc.create(new ShoppingRequest("盐", 0, "袋", null, null), 1L));
    }

    @Test
    void createRejectsNegativeQuantity() {
        ApiException ex = assertThrows(ApiException.class, () -> svc().create(req(-1), 1L));
        assertEquals("INVALID_QUANTITY", ex.getCode());
    }

    @Test
    void createWithMissingCategoryThrows404() {
        when(categoryRepository.existsById(99L)).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class,
                () -> svc().create(new ShoppingRequest("盐", 1, "袋", 99L, null), 1L));
        assertEquals("CATEGORY_NOT_FOUND", ex.getCode());
    }

    @Test
    void createSuccessSetsPendingAndCreator() {
        when(repo.save(any(ShoppingItem.class))).thenAnswer(inv -> {
            ShoppingItem i = inv.getArgument(0);
            i.setId(3L);
            return i;
        });
        ShoppingItemResponse resp = svc().create(req(2), 7L);
        assertEquals("盐", resp.name());
        assertEquals(ShoppingStatus.PENDING.name(), resp.status());
        assertEquals(7L, resp.createdBy());
        assertNull(resp.completedAt());
    }

    @Test
    void listNullStatusReturnsAllDesc() {
        when(repo.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(item(1L, ShoppingStatus.PENDING)));
        List<ShoppingItemResponse> result = svc().list(null);
        assertEquals(1, result.size());
        assertEquals(ShoppingStatus.PENDING.name(), result.get(0).status());
    }

    @Test
    void listByStatusFilters() {
        when(repo.findByStatusOrderByCreatedAtDesc(ShoppingStatus.BOUGHT))
                .thenReturn(List.of(item(2L, ShoppingStatus.BOUGHT)));
        List<ShoppingItemResponse> result = svc().list(ShoppingStatus.BOUGHT);
        assertEquals(1, result.size());
        assertEquals("BOUGHT", result.get(0).status());
    }

    @Test
    void updateMissingItemThrows404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> svc().update(99L, req(1)));
        assertEquals("SHOPPING_ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateAppliesFields() {
        ShoppingItem existing = item(1L, ShoppingStatus.PENDING);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(ShoppingItem.class))).thenAnswer(inv -> inv.getArgument(0));
        ShoppingItemResponse resp = svc().update(1L, new ShoppingRequest("生抽", 3, "瓶", null, "特价"));
        assertEquals("生抽", resp.name());
        assertEquals(3, resp.quantity());
        assertEquals("瓶", resp.unit());
        assertEquals("特价", resp.remark());
        assertEquals(ShoppingStatus.PENDING.name(), resp.status());
    }

    @Test
    void setStatusMissingItemThrows404() {
        when(repo.findById(99L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> svc().setStatus(99L, ShoppingStatus.BOUGHT));
        assertEquals("SHOPPING_ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    void setStatusBoughtWritesCompletedAt() {
        ShoppingItem existing = item(1L, ShoppingStatus.PENDING);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(ShoppingItem.class))).thenAnswer(inv -> inv.getArgument(0));
        ShoppingItemResponse resp = svc().setStatus(1L, ShoppingStatus.BOUGHT);
        assertEquals(ShoppingStatus.BOUGHT.name(), resp.status());
        assertNotNull(resp.completedAt());
    }

    @Test
    void setStatusCancelledWritesCompletedAt() {
        ShoppingItem existing = item(1L, ShoppingStatus.PENDING);
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(ShoppingItem.class))).thenAnswer(inv -> inv.getArgument(0));
        ShoppingItemResponse resp = svc().setStatus(1L, ShoppingStatus.CANCELLED);
        assertEquals(ShoppingStatus.CANCELLED.name(), resp.status());
        assertNotNull(resp.completedAt());
    }

    @Test
    void setStatusBackToPendingClearsCompletedAt() {
        ShoppingItem existing = item(1L, ShoppingStatus.BOUGHT);
        existing.setCompletedAt(LocalDateTime.now());
        when(repo.findById(1L)).thenReturn(Optional.of(existing));
        when(repo.save(any(ShoppingItem.class))).thenAnswer(inv -> inv.getArgument(0));
        ShoppingItemResponse resp = svc().setStatus(1L, ShoppingStatus.PENDING);
        assertEquals(ShoppingStatus.PENDING.name(), resp.status());
        assertNull(resp.completedAt());
    }
}