package com.family.storage.shopping;

import com.family.storage.category.CategoryRepository;
import com.family.storage.common.ApiException;
import com.family.storage.shopping.dto.ShoppingItemResponse;
import com.family.storage.shopping.dto.ShoppingRequest;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 购物清单业务（spec §5.5）：
 * - list(status) 状态为空查全部，均按创建时间倒序；
 * - create 校验数量为正（400 INVALID_QUANTITY）；分类存在才允许挂接（404 CATEGORY_NOT_FOUND）；
 * - update 更新名称/数量/单位/分类/备注，不改变状态；
 * - setStatus 流转状态：置 BOUGHT/CANCELLED 写 completed_at，回到 PENDING 清空 completed_at；
 * - 不存在的 id 统一 404 SHOPPING_ITEM_NOT_FOUND。
 * 便捷构造器允许 categoryRepository 为 null（仅供单元测试），此时跳过分类校验。
 */
@Service
public class ShoppingItemService {

    private final ShoppingItemRepository shoppingItemRepository;
    private final CategoryRepository categoryRepository;

    public ShoppingItemService(ShoppingItemRepository shoppingItemRepository,
                               CategoryRepository categoryRepository) {
        this.shoppingItemRepository = shoppingItemRepository;
        this.categoryRepository = categoryRepository;
    }

    public List<ShoppingItemResponse> list(ShoppingStatus status) {
        if (status == null) {
            return shoppingItemRepository.findAllByOrderByCreatedAtDesc()
                    .stream().map(this::toResponse).toList();
        }
        return shoppingItemRepository.findByStatusOrderByCreatedAtDesc(status)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ShoppingItemResponse create(ShoppingRequest req, Long userId) {
        validateQuantity(req.quantity());
        validateCategory(req.categoryId());
        ShoppingItem item = new ShoppingItem();
        apply(item, req);
        item.setStatus(ShoppingStatus.PENDING);
        item.setCreatedBy(userId);
        return toResponse(shoppingItemRepository.save(item));
    }

    @Transactional
    public ShoppingItemResponse update(Long id, ShoppingRequest req) {
        ShoppingItem item = find(id);
        validateQuantity(req.quantity());
        validateCategory(req.categoryId());
        apply(item, req);
        return toResponse(shoppingItemRepository.save(item));
    }

    /**
     * 状态流转：BOUGHT/CANCELLED 视为完成动作并写 completed_at，
     * 回到 PENDING 时清空 completed_at（回到待购可再次完成）。
     */
    @Transactional
    public ShoppingItemResponse setStatus(Long id, ShoppingStatus status) {
        ShoppingItem item = find(id);
        item.setStatus(status);
        if (status == ShoppingStatus.BOUGHT || status == ShoppingStatus.CANCELLED) {
            item.setCompletedAt(LocalDateTime.now());
        } else {
            item.setCompletedAt(null);
        }
        return toResponse(shoppingItemRepository.save(item));
    }

    private void apply(ShoppingItem item, ShoppingRequest req) {
        item.setName(req.name());
        item.setQuantity(req.quantity());
        item.setUnit(req.unit());
        item.setCategoryId(req.categoryId());
        item.setRemark(req.remark());
    }

    private void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "数量必须大于 0");
        }
    }

    private void validateCategory(Long categoryId) {
        if (categoryId != null && categoryRepository != null && !categoryRepository.existsById(categoryId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "分类不存在");
        }
    }

    private ShoppingItem find(Long id) {
        return shoppingItemRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "SHOPPING_ITEM_NOT_FOUND", "购物清单项不存在"));
    }

    private ShoppingItemResponse toResponse(ShoppingItem i) {
        return new ShoppingItemResponse(i.getId(), i.getName(), i.getQuantity(), i.getUnit(),
                i.getCategoryId(), i.getRemark(), i.getStatus().name(), i.getCreatedBy(),
                i.getCreatedAt(), i.getCompletedAt());
    }
}