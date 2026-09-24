package com.family.storage;

import com.family.storage.category.CategoryRepository;
import com.family.storage.category.CategoryService;
import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {
    @Mock CategoryRepository categoryRepository;
    @Mock ItemRepository itemRepository;

    @Test
    void deleteCategoryWithItemsIsRejected() {
        when(itemRepository.countByCategoryId(1L)).thenReturn(3L);
        var svc = new CategoryService(categoryRepository, itemRepository);
        assertThrows(ApiException.class, () -> svc.delete(1L));
    }
}