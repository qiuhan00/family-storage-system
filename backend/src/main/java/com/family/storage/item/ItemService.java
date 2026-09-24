package com.family.storage.item;

import com.family.storage.category.CategoryRepository;
import com.family.storage.common.ApiException;
import com.family.storage.common.PageResponse;
import com.family.storage.item.dto.ItemRequest;
import com.family.storage.item.dto.ItemResponse;
import com.family.storage.location.LocationRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 物品业务：CRUD + 分页检索 + 临期列表（spec §5.4）。
 * - create 仅落 items 表；INITIAL 出入库审计由 Task 5 随 stock_changes 一并实现
 *   （spec §5.4 语义届时达成，本任务不写 stock_changes）。
 * - search 未传 status 时只查 ACTIVE；expiring=true 命中 [today, today+7] 临期窗口
 *   （默认阈值与 app_settings 种子 expiry.remind.days=7 一致）。
 * - search 默认按创建时间倒序（createdAt desc）。
 * - 外键 category/location 不存在→404；quantity<0→400 NEGATIVE_QUANTITY。
 */
@Service
@RequiredArgsConstructor
public class ItemService {

    /** search 的 expiring 过滤窗口天数：与 app_settings 种子 expiry.remind.days=7 保持一致。 */
    public static final int SEARCH_EXPIRING_DAYS = 7;

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    public PageResponse<ItemResponse> search(String keyword, Long categoryId, Long locationId,
                                             String status, boolean expiring, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        ItemStatus st = (status == null || status.isBlank()) ? ItemStatus.ACTIVE : parseStatus(status);
        LocalDate today = LocalDate.now();
        LocalDate from = expiring ? today : null;
        LocalDate to = expiring ? today.plusDays(SEARCH_EXPIRING_DAYS) : null;
        Pageable sorted = pageable.getSort().isUnsorted()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
                        Sort.by(Sort.Direction.DESC, "createdAt"))
                : pageable;
        Page<Item> page = itemRepository.search(kw, categoryId, locationId, st, from, to, sorted);
        return PageResponse.of(page.map(this::toResponse));
    }

    public List<ItemResponse> expiringList(int days) {
        LocalDate today = LocalDate.now();
        return itemRepository
                .findByStatusAndExpiryDateBetweenOrderByExpiryDateAsc(ItemStatus.ACTIVE, today, today.plusDays(days))
                .stream().map(this::toResponse).toList();
    }

    public ItemResponse get(Long id) {
        return toResponse(find(id));
    }

    public ItemResponse create(ItemRequest req, Long userId) {
        validateReferences(req);
        validateQuantity(req.quantity());
        Item item = new Item();
        apply(item, req);
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedBy(userId);
        return toResponse(itemRepository.save(item));
    }

    public ItemResponse update(Long id, ItemRequest req) {
        Item item = find(id);
        validateReferences(req);
        validateQuantity(req.quantity());
        apply(item, req);
        return toResponse(itemRepository.save(item));
    }

    public void delete(Long id) {
        itemRepository.delete(find(id));
    }

    private void apply(Item item, ItemRequest req) {
        item.setName(req.name());
        item.setCategoryId(req.categoryId());
        item.setLocationId(req.locationId());
        item.setQuantity(req.quantity());
        item.setUnit(req.unit());
        item.setPurchasePrice(req.purchasePrice());
        item.setPurchaseDate(req.purchaseDate());
        item.setExpiryDate(req.expiryDate());
        item.setPhotoUrl(req.photoUrl());
        item.setRemark(req.remark());
    }

    private void validateReferences(ItemRequest req) {
        if (!categoryRepository.existsById(req.categoryId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "分类不存在");
        }
        if (!locationRepository.existsById(req.locationId())) {
            throw new ApiException(HttpStatus.NOT_FOUND, "LOCATION_NOT_FOUND", "位置不存在");
        }
    }

    private void validateQuantity(int quantity) {
        if (quantity < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "NEGATIVE_QUANTITY", "数量不能为负数");
        }
    }

    private ItemStatus parseStatus(String status) {
        try {
            return ItemStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STATUS", "无效的物品状态: " + status);
        }
    }

    private Item find(Long id) {
        return itemRepository.findById(id).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ITEM_NOT_FOUND", "物品不存在"));
    }

    private ItemResponse toResponse(Item i) {
        return new ItemResponse(i.getId(), i.getName(), i.getCategoryId(), i.getLocationId(),
                i.getQuantity(), i.getUnit(), i.getPurchasePrice(), i.getPurchaseDate(),
                i.getExpiryDate(), i.getPhotoUrl(), i.getStatus().name(), i.getRemark(),
                i.getCreatedBy(), i.getCreatedAt());
    }
}