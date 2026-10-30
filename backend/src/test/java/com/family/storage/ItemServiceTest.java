package com.family.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.family.storage.category.CategoryRepository;
import com.family.storage.common.ApiException;
import com.family.storage.common.PageResponse;
import com.family.storage.item.Item;
import com.family.storage.item.ItemRepository;
import com.family.storage.item.ItemService;
import com.family.storage.item.ItemStatus;
import com.family.storage.item.dto.ItemRequest;
import com.family.storage.item.dto.ItemResponse;
import com.family.storage.location.LocationRepository;
import com.family.storage.settings.SettingsService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock LocationRepository locationRepository;
    @Mock SettingsService settingsService;
    @Captor ArgumentCaptor<ItemStatus> statusCaptor;
    @Captor ArgumentCaptor<LocalDate> fromCaptor;
    @Captor ArgumentCaptor<LocalDate> toCaptor;

    private ItemService svc() {
        return new ItemService(itemRepository, categoryRepository, locationRepository, null, settingsService);
    }

    private ItemRequest req(Long categoryId, Long locationId, int quantity) {
        return new ItemRequest("牛奶", categoryId, locationId, quantity, "盒",
                new BigDecimal("5.9"), LocalDate.now(), LocalDate.now().plusDays(20), null, null, null, null);
    }

    private Item item() {
        Item i = new Item();
        i.setId(1L);
        i.setName("牛奶");
        i.setCategoryId(1L);
        i.setLocationId(1L);
        i.setQuantity(3);
        i.setUnit("盒");
        i.setStatus(ItemStatus.ACTIVE);
        i.setCreatedBy(7L);
        return i;
    }

    @Test
    void createItemWithMissingCategoryThrows404() {
        when(categoryRepository.existsById(999L)).thenReturn(false);
        var req = new ItemRequest("牛奶", 999L, 1L, 3, "盒",
                new BigDecimal("5.9"), LocalDate.now(), LocalDate.now().plusDays(20), null, null, null, null);
        assertThrows(ApiException.class, () -> svc().create(req, 1L));
    }

    @Test
    void quantityCannotBeNegative() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);
        var req = new ItemRequest("可乐", 1L, 1L, -1, "瓶", null, null, null, null, null, null, null);
        ApiException ex = assertThrows(ApiException.class, () -> svc().create(req, 1L));
        assertEquals("NEGATIVE_QUANTITY", ex.getCode());
    }

    @Test
    void createWithMissingLocationThrows404() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(false);
        ApiException ex = assertThrows(ApiException.class, () -> svc().create(req(1L, 1L, 3), 1L));
        assertEquals("LOCATION_NOT_FOUND", ex.getCode());
    }

    @Test
    void createSuccessSetsActiveStatusAndCreator() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setId(5L);
            return i;
        });
        ItemResponse resp = svc().create(req(1L, 1L, 3), 7L);
        assertEquals("牛奶", resp.name());
        assertEquals(ItemStatus.ACTIVE.name(), resp.status());
        assertEquals(7L, resp.createdBy());
        assertEquals(new BigDecimal("5.9"), resp.purchasePrice());
    }

    @Test
    void createPersistsLocationDetailAndReturnsIt() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setId(9L);
            return i;
        });
        var req = new ItemRequest("牛奶", 1L, 1L, 3, "盒",
                new BigDecimal("5.9"), null, null, null, null, "冰箱上层", null);
        ItemResponse resp = svc().create(req, 7L);
        assertEquals("冰箱上层", resp.locationDetail());
    }

    @Test
    void createPersistsLowStockThresholdAndReturnsIt() {
        when(categoryRepository.existsById(1L)).thenReturn(true);
        when(locationRepository.existsById(1L)).thenReturn(true);
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> {
            Item i = inv.getArgument(0);
            i.setId(10L);
            return i;
        });
        var req = new ItemRequest("牛奶", 1L, 1L, 1, "盒",
                new BigDecimal("5.9"), null, null, null, null, null, 5);
        ItemResponse resp = svc().create(req, 7L);
        assertEquals(5, resp.lowStockThreshold());
    }

    @Test
    void lowStockListQueriesActiveItems() {
        Item low = item();
        low.setQuantity(1);
        low.setLowStockThreshold(3);
        when(itemRepository.findLowStock(ItemStatus.ACTIVE)).thenReturn(List.of(low));
        List<ItemResponse> result = svc().lowStockList();
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).lowStockThreshold());
    }

    @Test
    void getMissingItemThrows404() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        ApiException ex = assertThrows(ApiException.class, () -> svc().get(99L));
        assertEquals("ITEM_NOT_FOUND", ex.getCode());
    }

    @Test
    void updateMissingItemThrows404() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> svc().update(99L, req(1L, 1L, 3)));
    }

    @Test
    void updateWithMissingCategoryThrows404() {
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item()));
        when(categoryRepository.existsById(999L)).thenReturn(false);
        assertThrows(ApiException.class, () -> svc().update(1L, req(999L, 1L, 3)));
    }

    @Test
    void deleteMissingItemThrows404() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ApiException.class, () -> svc().delete(99L));
    }

    @Test
    void searchDefaultsToActiveStatus() {
        when(itemRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
        PageResponse<ItemResponse> result = svc().search("奶", null, null, null, false, PageRequest.of(0, 10));
        assertEquals(1, result.content().size());
        verify(itemRepository).search(any(), any(), any(), statusCaptor.capture(),
                any(), any(), any(Pageable.class));
        assertEquals(ItemStatus.ACTIVE, statusCaptor.getValue());
    }

    @Test
    void searchBlankKeywordPassedAsNull() {
        when(itemRepository.search(isNull(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
        svc().search("  ", null, null, null, false, PageRequest.of(0, 10));
        verify(itemRepository).search(isNull(), any(), any(), any(), any(), any(), any(Pageable.class));
    }

    @Test
    void searchExpiringWindowFollowsConfiguredThreshold() {
        when(settingsService.getInt(SettingsService.EXPIRY_REMIND_DAYS,
                SettingsService.DEFAULT_EXPIRY_REMIND_DAYS)).thenReturn(30);
        when(itemRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
        svc().search(null, null, null, null, true, PageRequest.of(0, 10));
        verify(itemRepository).search(any(), any(), any(), any(),
                fromCaptor.capture(), toCaptor.capture(), any(Pageable.class));
        assertEquals(LocalDate.now(), fromCaptor.getValue());
        assertEquals(LocalDate.now().plusDays(30), toCaptor.getValue());
    }

    @Test
    void searchWithoutExpiringSkipsThresholdLookup() {
        when(itemRepository.search(any(), any(), any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(item())));
        svc().search(null, null, null, null, false, PageRequest.of(0, 10));
        verifyNoInteractions(settingsService);
    }

    @Test
    void expiringListQueriesActiveWithinWindow() {
        LocalDate today = LocalDate.now();
        when(itemRepository.findByStatusAndExpiryDateBetweenOrderByExpiryDateAsc(
                ItemStatus.ACTIVE, today, today.plusDays(3))).thenReturn(List.of(item()));
        List<ItemResponse> result = svc().expiringList(3);
        assertEquals(1, result.size());
        assertEquals(ItemStatus.ACTIVE.name(), result.get(0).status());
    }

    @Test
    void expiringListWithoutDaysFallsBackToThreshold() {
        when(settingsService.getInt(SettingsService.EXPIRY_REMIND_DAYS,
                SettingsService.DEFAULT_EXPIRY_REMIND_DAYS)).thenReturn(30);
        LocalDate today = LocalDate.now();
        when(itemRepository.findByStatusAndExpiryDateBetweenOrderByExpiryDateAsc(
                ItemStatus.ACTIVE, today, today.plusDays(30))).thenReturn(List.of(item()));
        List<ItemResponse> result = svc().expiringList(null);
        assertEquals(1, result.size());
    }
}