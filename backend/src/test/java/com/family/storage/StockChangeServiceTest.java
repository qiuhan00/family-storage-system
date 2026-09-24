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

    @Test
    void optimisticLockConflictPropagatesFor409Mapping() {
        // M2 回归：并发写时 save 抛乐观锁冲突（如 IN/OUT 同时基于旧 version 更新），
        // 服务层不该吞掉，应由全局异常映射为 409 STOCK_CONFLICT 提示重试。
        Item item = new Item();
        item.setId(1L); item.setQuantity(2); item.setVersion(1L);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(itemRepository.save(any(Item.class)))
                .thenThrow(new org.springframework.orm.ObjectOptimisticLockingFailureException(Item.class, 1L));
        var svc = new ItemService(itemRepository, null, null, stockChangeRepository);
        assertThrows(org.springframework.orm.ObjectOptimisticLockingFailureException.class,
                () -> svc.adjustStock(1L, new StockRequest(-1, "OUT", "并发出库"), 9L));
    }
}