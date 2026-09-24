package com.family.storage;

import com.family.storage.category.Category;
import com.family.storage.category.CategoryRepository;
import com.family.storage.category.CategoryService;
import com.family.storage.category.dto.CategoryRequest;
import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import java.util.Optional;
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

    @Test
    void createDuplicateNameIsRejected() {
        when(categoryRepository.findByName("食品")).thenReturn(Optional.of(category(1L, "食品")));
        var svc = new CategoryService(categoryRepository, itemRepository);
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.create(new CategoryRequest("食品", 0)));
        assertEquals("CATEGORY_NAME_TAKEN", ex.getCode());
    }

    @Test
    void updateKeepingOwnNameIsAllowed() {
        Category existing = category(1L, "食品");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByName("食品")).thenReturn(Optional.of(existing));
        var svc = new CategoryService(categoryRepository, itemRepository);
        assertDoesNotThrow(() -> svc.update(1L, new CategoryRequest("食品", 0)));
    }

    @Test
    void moveCategoryToTakenNameIsRejected() {
        Category existing = category(1L, "食品");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(categoryRepository.findByName("饮料")).thenReturn(Optional.of(category(2L, "饮料")));
        var svc = new CategoryService(categoryRepository, itemRepository);
        ApiException ex = assertThrows(ApiException.class,
                () -> svc.update(1L, new CategoryRequest("饮料", 0)));
        assertEquals("CATEGORY_NAME_TAKEN", ex.getCode());
    }

    private Category category(Long id, String name) {
        Category c = new Category();
        c.setId(id);
        c.setName(name);
        c.setSortOrder(0);
        return c;
    }
}