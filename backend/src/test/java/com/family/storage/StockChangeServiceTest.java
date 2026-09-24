package com.family.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.family.storage.common.ApiException;
import com.family.storage.item.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StockChangeServiceTest {
    @Mock ItemRepository itemRepository;
    @Mock StockChangeRepository stockChangeRepository;
    @Test
    void outMoreThanStockIsRejected() {
        Item item = new Item();
        item.setId(1L); item.setQuantity(2);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        var svc = new ItemService(itemRepository, null, null, stockChangeRepository);
        assertThrows(ApiException.class, () -> svc.adjustStock(1L, new StockRequest(-5, "OUT", "用到"), 9L));
    }
    @Test
    void adjustUpdatesQuantityAndWritesRecord() {
        Item item = new Item();
        item.setId(1L); item.setQuantity(2);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(stockChangeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        var svc = new ItemService(itemRepository, null, null, stockChangeRepository);
        svc.adjustStock(1L, new StockRequest(3, "IN", "补货"), 9L);
        assertEquals(5, item.getQuantity());
        verify(stockChangeRepository).save(any());
    }
}