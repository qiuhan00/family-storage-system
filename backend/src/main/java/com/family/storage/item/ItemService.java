package com.family.storage.item;

import com.family.storage.category.CategoryRepository;
import com.family.storage.common.ApiException;
import com.family.storage.common.PageResponse;
import com.family.storage.item.dto.ItemRequest;
import com.family.storage.item.dto.ItemResponse;
import com.family.storage.item.dto.StockChangeResponse;
import com.family.storage.location.LocationRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 物品业务：CRUD + 分页检索 + 临期列表 + 出入库审计（spec §5.4）。
 * - create 在 quantity>0 时同时写 1 条 INITIAL 出入库记录（R4 兑现点，
 *   spec §5.4 「新增（qty>0 时写 INITIAL 出入库记录）」），同为事务。
 * - delete 在同一事务内级联删除该物品的 stock_changes（deleteByItemId），
 *   删除失败不残留 items 记录。
 * - adjustStock 校验新 quantity>=0，type 与 delta 对齐（IN 需 delta>0，
 *   OUT 需 delta<0），ADJUST 供手动校准；stockHistory 按时间倒序返回历史。
 * - search 未传 status 时只查 ACTIVE；expiring=true 命中 [today, today+7] 临期窗口
 *   （默认阈值与 app_settings 种子 expiry.remind.days=7 一致）。
 * - search 默认按创建时间倒序（createdAt desc）。
 * - 外键 category/location 不存在→404；quantity<0→400 NEGATIVE_QUANTITY。
 */
@Service
public class ItemService {

    /** search 的 expiring 过滤窗口天数：与 app_settings 种子 expiry.remind.days=7 保持一致。 */
    public static final int SEARCH_EXPIRING_DAYS = 7;

    private final ItemRepository itemRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final StockChangeRepository stockChangeRepository;

    @Autowired
    public ItemService(ItemRepository itemRepository, CategoryRepository categoryRepository,
                       LocationRepository locationRepository, StockChangeRepository stockChangeRepository) {
        this.itemRepository = itemRepository;
        this.categoryRepository = categoryRepository;
        this.locationRepository = locationRepository;
        this.stockChangeRepository = stockChangeRepository;
    }

    /**
     * 便捷构造器（不注入出入库仓储）：仅供 Task 4 既有单元测试使用；
     * 生产注入走 4 参构造器。stockChangeRepository 为 null 时出入库审计方法不落记录。
     */
    public ItemService(ItemRepository itemRepository, CategoryRepository categoryRepository,
                       LocationRepository locationRepository) {
        this(itemRepository, categoryRepository, locationRepository, null);
    }

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

    @Transactional
    public ItemResponse create(ItemRequest req, Long userId) {
        validateReferences(req);
        validateQuantity(req.quantity());
        Item item = new Item();
        apply(item, req);
        item.setStatus(ItemStatus.ACTIVE);
        item.setCreatedBy(userId);
        Item saved = itemRepository.save(item);
        if (stockChangeRepository != null && saved.getQuantity() > 0) {
            saveChange(saved.getId(), saved.getQuantity(), StockType.INITIAL, null, userId);
        }
        return toResponse(saved);
    }

    public ItemResponse update(Long id, ItemRequest req) {
        Item item = find(id);
        validateReferences(req);
        validateQuantity(req.quantity());
        apply(item, req);
        return toResponse(itemRepository.save(item));
    }

    /** 删除物品并级联清除其出入库审计记录（同一事务，删除失败不残留）。 */
    @Transactional
    public void delete(Long id) {
        Item item = find(id);
        if (stockChangeRepository != null) {
            stockChangeRepository.deleteByItemId(id);
        }
        itemRepository.delete(item);
    }

    /**
     * 出入库调整（spec §5.4 POST /api/items/{id}/stock）：
     * 记录按 delta 正负与声明 type 对齐（IN 需 delta>0、OUT 需 delta<0），
     * ADJUST 供手动校准（任意 delta）；调整后存量不得为负。
     */
    @Transactional
    public StockChangeResponse adjustStock(Long itemId, StockRequest req, Long userId) {
        Item item = find(itemId);
        StockType type = parseStockType(req.type());
        int delta = req.deltaQty();
        validateDeltaAlignment(type, delta);
        int newQuantity = item.getQuantity() + delta;
        if (newQuantity < 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INSUFFICIENT_STOCK", "库存不足，出库数量超出当前存量");
        }
        item.setQuantity(newQuantity);
        itemRepository.save(item);
        return toChangeResponse(saveChange(itemId, delta, type, req.note(), userId));
    }

    /** 变更历史（spec §5.4 GET /api/items/{id}/stock-changes），按时间倒序。 */
    public List<StockChangeResponse> stockHistory(Long itemId) {
        find(itemId);
        if (stockChangeRepository == null) {
            return List.of();
        }
        return stockChangeRepository.findByItemIdOrderByCreatedAtDesc(itemId)
                .stream().map(this::toChangeResponse).toList();
    }

    private StockChange saveChange(Long itemId, int changeQty, StockType type, String note, Long userId) {
        StockChange change = new StockChange();
        change.setItemId(itemId);
        change.setChangeQty(changeQty);
        change.setType(type);
        change.setNote(note);
        change.setCreatedBy(userId);
        return stockChangeRepository.save(change);
    }

    private StockType parseStockType(String type) {
        try {
            StockType parsed = StockType.valueOf(type.trim().toUpperCase());
            if (parsed == StockType.INITIAL) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_TYPE", "INITIAL 为系统内部类型，不允许手动提交");
            }
            return parsed;
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_STOCK_TYPE", "无效的出入库类型: " + type);
        }
    }

    private void validateDeltaAlignment(StockType type, int delta) {
        if (type == StockType.IN && delta <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_DELTA_MISMATCH", "入库操作 deltaQty 必须大于 0");
        }
        if (type == StockType.OUT && delta >= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "TYPE_DELTA_MISMATCH", "出库操作 deltaQty 必须小于 0");
        }
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

    private StockChangeResponse toChangeResponse(StockChange c) {
        return new StockChangeResponse(c.getId(), c.getItemId(), c.getChangeQty(),
                c.getType().name(), c.getNote(), c.getCreatedBy(), c.getCreatedAt());
    }
}