package com.family.storage.category;

import com.family.storage.category.dto.CategoryRequest;
import com.family.storage.common.ApiException;
import com.family.storage.item.ItemRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 分类业务：列表、增改查删。删除前校验分类下仍有物品则拒绝
 * （spec §4 关联约束、§5.3 DELETE 400）。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ItemRepository itemRepository;

    public List<Category> list() {
        return categoryRepository.findAllByOrderBySortOrderAsc();
    }

    public Category get(Long id) {
        return find(id);
    }

    public Long create(CategoryRequest req) {
        Category c = new Category();
        c.setName(req.name());
        c.setSortOrder(req.sortOrderOrDefault());
        return categoryRepository.save(c).getId();
    }

    public Category update(Long id, CategoryRequest req) {
        Category c = find(id);
        c.setName(req.name());
        c.setSortOrder(req.sortOrderOrDefault());
        return categoryRepository.save(c);
    }

    public void delete(Long id) {
        if (itemRepository.countByCategoryId(id) > 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CATEGORY_IN_USE",
                    "该分类下仍有物品，请先迁移或删除物品");
        }
        categoryRepository.delete(find(id));
    }

    private Category find(Long id) {
        return categoryRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "分类不存在"));
    }
}